package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.MvcResult
import jakarta.servlet.http.Cookie
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.security.crypto.password.PasswordEncoder
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.annotation.DirtiesContext
import org.springframework.boot.test.context.TestConfiguration
import br.com.ajadigital.core.adapter.out.messaging.OutboxOperationsController
import br.com.ajadigital.core.adapter.out.messaging.OutboxPublisher
import br.com.ajadigital.core.adapter.out.messaging.OutboxRedrive

class MutableTestClock : Clock() {
    private val offset = AtomicReference(Duration.ZERO)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.now(Clock.systemUTC()).plus(offset.get())
    fun advance(duration: Duration) { offset.updateAndGet { it.plus(duration) } }
}

@TestConfiguration
class MutableTestClockConfiguration {
    @Bean
    @Primary
    fun mutableTestClock() = MutableTestClock()
}

@TestConfiguration
class EmailQueueTopologyTestConfiguration {
    @Bean
    fun emailQueues(exchange: TopicExchange): Declarables {
        val eventQueue = QueueBuilder.durable("email.identity.events")
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", "email.identity.dlq")
            .build()
        val retryQueue = QueueBuilder.durable("email.identity.retry")
            .withArgument("x-message-ttl", 5000)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", "email.identity.events")
            .build()
        val deadLetterQueue = QueueBuilder.durable("email.identity.dlq").build()
        return Declarables(
            eventQueue,
            retryQueue,
            deadLetterQueue,
            BindingBuilder.bind(eventQueue).to(exchange).with("identity.email-confirmation-requested"),
            BindingBuilder.bind(eventQueue).to(exchange).with("identity.password-reset-requested"),
        )
    }
}

@Testcontainers
@AutoConfigureMockMvc
@Import(MutableTestClockConfiguration::class, EmailQueueTopologyTestConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
class CoreApplicationTests @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val flyway: Flyway,
    private val mvc: MockMvc,
    private val rabbitTemplate: RabbitTemplate,
    private val outboxPublisher: OutboxPublisher,
    private val passwordEncoder: PasswordEncoder,
    private val mutableClock: MutableTestClock,
) {
    @Test
    fun outboxOperatorRejectsBlankConfiguredSecret() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboxOperationsController(OutboxRedrive(jdbc, mutableClock), " \t")
        }
        assertEquals(401, OutboxOperationsController(OutboxRedrive(jdbc, mutableClock), "a-valid-secret")
            .redrive("not-a-uuid", " \t").statusCode.value())
    }

    @Test
    fun registrationCreatesStudentAndReturnsContractResponse() {
        val email = "student-${UUID.randomUUID()}@example.test"
        val registration = mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Test Student","email":"$email","password":"A long test password"}"""
        }
        registration.andExpect {
            status { isCreated() }
            jsonPath("$.role") { value("student") }
            jsonPath("$.emailConfirmedAt") { value(null) }
        }
        val response: MvcResult = registration.andReturn()
        val hash = jdbc.queryForObject("select password_hash from core.users where normalized_email = ?", String::class.java, email)!!
        assertTrue(passwordEncoder.matches("A long test password", hash))
        assertFalse(hash.contains("A long test password"))
        val tokenHash = jdbc.queryForObject("select token_hash from core.auth_tokens where email_at_issue = ?", String::class.java, email)!!
        assertTrue(tokenHash.matches(Regex("[a-f0-9]{64}")))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.sessions where user_id = (select id from core.users where normalized_email = ?)", Int::class.java, email))
        assertFalse(response.response.contentAsString.contains("A long test password"))
        val event = jdbc.queryForMap("select id, nonce, encrypted_payload from core.outbox_events where event_type = ? order by created_at desc limit 1", "identity.email-confirmation-requested")
        val eventId = event["id"] as String
        val nonce = event["nonce"] as ByteArray
        val encryptedPayload = event["encrypted_payload"] as ByteArray
        assertFalse(String(encryptedPayload).contains(email))
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, SecretKeySpec(ByteArray(32) { 7 }, "AES"), GCMParameterSpec(128, nonce))
        decrypt.updateAAD("$eventId|identity.email-confirmation-requested|1".toByteArray())
        val payload = String(decrypt.doFinal(encryptedPayload))
        val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        assertTrue(payload.contains(email))
        assertEquals(tokenHash, MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) })
        assertFalse(response.response.contentAsString.contains(token))
        awaitPublished(eventId)
        val published = receivePublishedEvent(eventId)
        assertTrue(published.contains(eventId))
        assertFalse(published.contains(token))
    }

    @Test
    fun missingOriginRejectsRegistrationBeforeAnyDatabaseWrite() {
        val email = "origin-rejected-${UUID.randomUUID()}@example.test"
        val usersBefore = jdbc.queryForObject("select count(*) from core.users", Int::class.java)
        val tokensBefore = jdbc.queryForObject("select count(*) from core.auth_tokens", Int::class.java)
        val eventsBefore = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"name":"Origin Student","email":"$email","password":"A long test password"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("FORBIDDEN") }
        }
        assertEquals(usersBefore, jdbc.queryForObject("select count(*) from core.users", Int::class.java))
        assertEquals(tokensBefore, jdbc.queryForObject("select count(*) from core.auth_tokens", Int::class.java))
        assertEquals(eventsBefore, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.users where normalized_email=?", Int::class.java, email))
    }

    @Test
    fun failedOutboxEventRedriveRequiresOperatorSecretAndPreservesCiphertext() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 3 }
        val ciphertext = ByteArray(48) { 9 }
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,
               attempt_count,next_attempt_at,last_error,failed_at) values(?, ?, 1, now(), 'fixture-key', ?, ?, 5, now(), 'IOException', now())""",
            id, "identity.email-confirmation-requested", nonce, ciphertext,
        )

        mvc.post("/internal/ops/outbox/$id/redrive") {
            header("Origin", "https://aja.example.test")
            header("X-AJA-Operator-Token", "wrong-operator-token")
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/internal/ops/outbox/$id/redrive") {
            header("Origin", "https://aja.example.test")
            header("X-AJA-Operator-Token", "fixture-outbox-operator-token-1234567890")
        }.andExpect { status { isAccepted() } }

        val row = jdbc.queryForMap("select nonce,encrypted_payload,attempt_count,failed_at from core.outbox_events where id=?", id)
        assertTrue(nonce.contentEquals(row["nonce"] as ByteArray))
        assertTrue(ciphertext.contentEquals(row["encrypted_payload"] as ByteArray))
        assertEquals(0, row["attempt_count"])
        assertEquals(null, row["failed_at"])
    }

    @Test
    fun publisherRepublishesConfirmedEventWhenOutboxMarkingFails() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 5 }
        val ciphertext = ByteArray(48) { 8 }
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,next_attempt_at)
               values(?, 'identity.email-confirmation-requested', 1, now(), 'fixture-key', ?, ?, ?)""",
            id, nonce, ciphertext, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(30)),
        )
        try {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("create sequence core.b03_outbox_failure_once")
                    statement.execute("grant usage, select on sequence core.b03_outbox_failure_once to core")
                    statement.execute(
                        """create function core.b03_fail_outbox_mark_once() returns trigger language plpgsql as ${'$'}${'$'}
                           begin
                             if new.id = '$id' and old.published_at is null and new.published_at is not null
                               and nextval('core.b03_outbox_failure_once') = 1 then
                               raise exception 'one-shot test failure after broker confirm';
                             end if;
                             return new;
                           end;
                           ${'$'}${'$'}""",
                    )
                    statement.execute(
                        "create trigger b03_fail_outbox_mark_once before update on core.outbox_events for each row execute function core.b03_fail_outbox_mark_once()",
                    )
                }
            }
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)

            val publishedBodies = mutableListOf<String>()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
            while (publishedBodies.size < 2 && System.nanoTime() < deadline) {
                val body = rabbitTemplate.receive("email.identity.events", 250)?.body?.let(::String) ?: continue
                if (Regex(""""id":"([^"]+)"""").find(body)?.groupValues?.get(1) == id) publishedBodies += body
            }

            assertEquals(2, publishedBodies.size, "Expected redelivery after confirmed publish could not be marked in PostgreSQL")
            assertEquals(publishedBodies.first(), publishedBodies.last(), "At-least-once retry must preserve the same event id and encrypted payload")
            assertEquals(true, jdbc.queryForObject("select published_at is not null from core.outbox_events where id=?", Boolean::class.java, id))
            assertEquals(1, jdbc.queryForObject("select attempt_count from core.outbox_events where id=?", Int::class.java, id))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists b03_fail_outbox_mark_once on core.outbox_events")
                    statement.execute("drop function if exists core.b03_fail_outbox_mark_once()")
                    statement.execute("drop sequence if exists core.b03_outbox_failure_once")
                }
            }
        }
    }

    @Test
    fun publisherKeepsOutboxPendingUntilEmailQueueIsBound() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 6 }
        val ciphertext = ByteArray(48) { 7 }
        val future = java.sql.Timestamp.from(mutableClock.instant().plusSeconds(30))
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,next_attempt_at)
               values(?, 'identity.email-confirmation-requested', 1, now(), 'fixture-key', ?, ?, ?)""",
            id, nonce, ciphertext, future,
        )

        try {
            rabbitTemplate.execute { channel ->
                listOf("email.identity.events", "email.identity.retry", "email.identity.dlq").forEach(channel::queueDelete)
            }
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)
            outboxPublisher.publishAvailable()

            assertEquals(null, jdbc.queryForObject("select published_at from core.outbox_events where id=?", java.sql.Timestamp::class.java, id),
                "An unroutable publisher-confirmed event must remain pending")
            assertEquals(1, jdbc.queryForObject("select attempt_count from core.outbox_events where id=?", Int::class.java, id))
            assertEquals(true, jdbc.queryForObject("select last_error is not null from core.outbox_events where id=?", Boolean::class.java, id),
                "The broker return must be recorded as an observable publish failure")

            declareEmailQueueTopology()
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)
            outboxPublisher.publishAvailable()
            awaitPublished(id)
            assertTrue(receivePublishedEvent(id).contains(id), "Event should arrive after Email declares and binds its queue")
        } finally {
            declareEmailQueueTopology()
        }
    }

    @Test
    fun unknownLoginIsAuditedWithoutAccountIdentifiersOrCredentials() {
        val email = "unknown-${UUID.randomUUID()}@example.test"
        val password = "Never store this password"
        mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isUnauthorized() } }

        val row = jdbc.queryForMap(
            "select event_type, outcome, subject_user_id from core.audit_events where event_type='auth.login' order by occurred_at desc limit 1",
        )
        assertEquals("auth.login", row["event_type"])
        assertEquals("failure", row["outcome"])
        assertEquals(null, row["subject_user_id"])
        val columns = jdbc.queryForList(
            "select column_name from information_schema.columns where table_schema='core' and table_name='audit_events'",
            String::class.java,
        )
        assertTrue(columns.none { column -> listOf("email", "password", "token", "session", "cookie").any { column?.contains(it) == true } })
        assertFalse(row.values.any { it?.toString()?.contains(email) == true || it?.toString()?.contains(password) == true })
    }

    @Test
    fun loginRejectsMalformedEmailWithValidationErrorBeforeAuthentication() {
        val auditBefore = jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java)
        mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"not-an-email","password":"any password"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }
        assertEquals(auditBefore, jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java))
    }

    @Test
    fun registrationRejectsMalformedEmailAddresses() {
        listOf("a@", "x@@y").forEach { email ->
            mvc.post("/api/v1/auth/register") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = """{"name":"Malformed Email","email":"$email","password":"A long test password"}"""
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            }
            assertEquals(0, jdbc.queryForObject("select count(*) from core.users where normalized_email=?", Int::class.java, email))
        }
    }

    @Test
    fun duplicateRegistrationReturnsConflictWithoutChangingStoredIdentity() {
        val email = "duplicate-${UUID.randomUUID()}@example.test"
        val request = """{"name":"Duplicate Student","email":"$email","password":"A long test password"}"""
        repeat(2) { attempt ->
            val result = mvc.post("/api/v1/auth/register") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = request
            }
            if (attempt == 0) result.andExpect { status { isCreated() } }
            else result.andExpect {
                status { isConflict() }
                jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            }
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from core.users where normalized_email=?", Int::class.java, email))
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue=? and purpose='email_confirmation'", Int::class.java, email))
    }

    @Test
    fun confirmationRequestsAreNeutralAndOnlyKnownAddressQueuesEncryptedEvent() {
        val email = "confirm-request-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Confirmation Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val originalTokenHash = jdbc.queryForObject("select token_hash from core.auth_tokens where email_at_issue=? and purpose='email_confirmation'", String::class.java, email)!!.trim()
        val beforeEvents = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        val known = mvc.post("/api/v1/auth/email-confirmation-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.message") { exists() }
        }.andReturn()
        val unknown = mvc.post("/api/v1/auth/email-confirmation-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"unknown-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        assertEquals(known.response.contentAsString, unknown.response.contentAsString)
        assertEquals(beforeEvents + 1, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        val payload = latestOutboxPayload("identity.email-confirmation-requested")
        val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        val tokenHash = jdbc.queryForObject(
            "select token_hash from core.auth_tokens where user_id = (select id from core.users where normalized_email = ?) and purpose = 'email_confirmation' and used_at is null",
            String::class.java, email,
        )!!
        assertEquals(sha256(token), tokenHash.trim())
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, originalTokenHash))
        assertFalse(known.response.contentAsString.contains(token))
    }

    @Test
    fun passwordResetRequestsAreNeutralForKnownAndUnknownAddresses() {
        val email = "reset-request-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Reset Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val beforeEvents = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        val known = mvc.post("/api/v1/auth/password-reset-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email"}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.message") { exists() }
        }.andReturn()
        val unknown = mvc.post("/api/v1/auth/password-reset-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"unknown-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        assertEquals(known.response.contentAsString, unknown.response.contentAsString)
        assertEquals(beforeEvents + 1, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        val payload = latestOutboxPayload("identity.password-reset-requested")
        val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        val tokenHash = jdbc.queryForObject(
            "select token_hash from core.auth_tokens where user_id = (select id from core.users where normalized_email = ?) and purpose = 'password_reset' and used_at is null",
            String::class.java, email,
        )!!
        assertEquals(sha256(token), tokenHash.trim())
        assertFalse(known.response.contentAsString.contains(token))
    }

    @Test
    fun emailConfirmationConsumesTokenAndRejectsReuseAndExpiry() {
        fun registerAndGetToken(suffix: String): Pair<String, String> {
            val email = "confirmation-$suffix-${UUID.randomUUID()}@example.test"
            mvc.post("/api/v1/auth/register") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = """{"name":"Confirmation Student","email":"$email","password":"A long test password"}"""
            }.andExpect { status { isCreated() } }
            val payload = latestOutboxPayload("identity.email-confirmation-requested")
            val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
            return email to token
        }
        val (confirmedEmail, confirmationToken) = registerAndGetToken("valid")
        mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$confirmationToken"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(confirmedEmail) }
            jsonPath("$.emailConfirmedAt") { exists() }
        }
        mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$confirmationToken"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("TOKEN_INVALID") }
        }

        val (_, expiredToken) = registerAndGetToken("expired")
        jdbc.update("update core.auth_tokens set expires_at = now() - interval '1 second' where token_hash = ?", sha256(expiredToken))
        mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$expiredToken"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("TOKEN_EXPIRED") }
        }

        val (oldEmail, boundToken) = registerAndGetToken("changed-email")
        val replacementEmail = "replacement-${UUID.randomUUID()}@example.test"
        jdbc.update("update core.users set email=?,normalized_email=? where normalized_email=?", replacementEmail, replacementEmail, oldEmail)
        mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$boundToken"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("TOKEN_INVALID") }
        }
    }

    @Test
    fun confirmationTokenExpiringWhileWaitingForUserLockIsRejected() {
        val email = "lock-expiry-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Lock Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        jdbc.update("update core.auth_tokens set expires_at=? where token_hash=?", java.sql.Timestamp.from(mutableClock.instant().plusMillis(1500)), sha256(token))

        val lockConnection = DriverManager.getConnection("${postgres.jdbcUrl}&currentSchema=core", "core", "core-test")
        lockConnection.autoCommit = false
        lockConnection.prepareStatement("select id from core.users where normalized_email=? for update").use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<MvcResult> {
                mvc.post("/api/v1/auth/email-confirmations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    content = """{"token":"$token"}"""
                }.andReturn()
            }
            if (pending.isDone) {
                val early = pending.get()
                assertTrue(false, "Profile request finished before the held row lock was released: ${early.response.status}")
            }
            awaitLockWait("from core.users")
            mutableClock.advance(Duration.ofSeconds(2))
            lockConnection.commit()
            assertEquals(422, pending.get(10, TimeUnit.SECONDS).response.status)
        } finally {
            mutableClock.advance(Duration.ofSeconds(-2))
            runCatching { lockConnection.rollback() }
            lockConnection.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun passwordResetChangesHashRevokesSessionsAndPreservesConfirmationToken() {
        val email = "password-reset-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Reset Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val confirmationHash = jdbc.queryForObject("select token_hash from core.auth_tokens where email_at_issue=? and purpose='email_confirmation'", String::class.java, email)!!.trim()
        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val sessionCookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')

        fun requestReset(): String = mvc.post("/api/v1/auth/password-reset-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email"}"""
        }.andExpect { status { isAccepted() } }.andReturn().let {
            val payload = latestOutboxPayload("identity.password-reset-requested")
            Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        }
        val previousToken = requestReset()
        val previousHash = sha256(previousToken)
        val resetToken = requestReset()
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue=? and purpose='password_reset' and used_at is null", Int::class.java, email))
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, previousHash))

        val newPassword = "Brand new long password"
        mvc.post("/api/v1/auth/password-resets") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$resetToken","password":"$newPassword"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.message") { exists() }
        }
        val passwordHash = jdbc.queryForObject("select password_hash from core.users where normalized_email=?", String::class.java, email)!!
        assertTrue(passwordEncoder.matches(newPassword, passwordHash))
        assertFalse(passwordEncoder.matches("A long test password", passwordHash))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.sessions where token_hash=? and revoked_at is null", Int::class.java, sha256(sessionCookie)))
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is null", Int::class.java, confirmationHash))
        mvc.get("/api/v1/me") { cookie(Cookie("aja_session", sessionCookie)) }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/auth/password-resets") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$resetToken","password":"Another long password"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("TOKEN_INVALID") }
        }
        mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"$newPassword"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun registrationRollsBackWhenOutboxWriteFails() {
        val email = "rollback-${UUID.randomUUID()}@example.test"
        val eventsBefore = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create or replace function core.reject_outbox() returns trigger language plpgsql as \$\$ begin raise exception 'forced outbox failure'; end \$\$")
                statement.execute("create trigger reject_outbox before insert on core.outbox_events for each row execute function core.reject_outbox()")
            }
        }
        try {
            val response = mvc.post("/api/v1/auth/register") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = """{"name":"Rollback Student","email":"$email","password":"A long test password"}"""
            }
            response.andExpect { status { isInternalServerError() } }
            assertEquals(0, jdbc.queryForObject("select count(*) from core.users where normalized_email = ?", Int::class.java, email))
            assertEquals(0, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue = ?", Int::class.java, email))
            assertEquals(eventsBefore, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists reject_outbox on core.outbox_events")
                    statement.execute("drop function if exists core.reject_outbox()")
                }
            }
        }
    }

    @Test
    fun loginCreatesOpaqueHttpOnlySessionCookie() {
        val email = "login-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Login Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val result = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.email") { value(email) }
            jsonPath("$.sessionId") { exists() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("aja_session=")) }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")) }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/api/v1")) }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")) }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=28800")) }
            header { string("Set-Cookie", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secure"))) }
            jsonPath("$.expiresAt") { exists() }
        }.andReturn()
        val sessionCookieValue = result.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        val sessionId = com.jayway.jsonpath.JsonPath.read<String>(result.response.contentAsString, "$.sessionId")
        val storedHash = jdbc.queryForObject("select token_hash from core.sessions where id = ?", String::class.java, sessionId)!!
        assertEquals(MessageDigest.getInstance("SHA-256").digest(sessionCookieValue.toByteArray()).joinToString("") { "%02x".format(it) }, storedHash.trim())
        assertEquals(28800.0, jdbc.queryForObject("select extract(epoch from expires_at - created_at) from core.sessions where id = ?", Double::class.java, sessionId))
        assertFalse(result.response.contentAsString.contains(sessionCookieValue))
        mvc.get("/api/v1/auth/session") { cookie(Cookie("aja_session", sessionCookieValue)) }.andExpect {
            status { isOk() }
            jsonPath("$.sessionId") { value(sessionId) }
            jsonPath("$.user.email") { value(email) }
        }
        mvc.get("/api/v1/me") { cookie(Cookie("aja_session", sessionCookieValue)) }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(email) }
        }
        jdbc.update("update core.users set active=false where normalized_email=?", email)
        mvc.get("/api/v1/me") { cookie(Cookie("aja_session", sessionCookieValue)) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("SESSION_REQUIRED") }
        }
    }

    @Test
    fun protectedReadsRequireSessionEvenWithoutOrigin() {
        mvc.get("/api/v1/auth/session").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("SESSION_REQUIRED") }
        }
        mvc.get("/api/v1/me") { header("Origin", "https://aja.example.test") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("SESSION_REQUIRED") }
        }
    }

    @Test
    fun profilePatchUsesSessionAndRevisionAndRejectsClientIdentityFields() {
        val email = "profile-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Profile Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val sessionCookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        val userId = com.jayway.jsonpath.JsonPath.read<String>(login.response.contentAsString, "$.user.id")
        mvc.patch("/api/v1/me") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", sessionCookie))
            content = """{"expectedRevision":1,"name":"Updated Name"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Updated Name") }
            jsonPath("$.revision") { value(2) }
            jsonPath("$.updatedBy") { value(userId) }
        }
        mvc.patch("/api/v1/me") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", sessionCookie))
            content = """{"expectedRevision":2,"name":null}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
        }
        assertEquals("Updated Name", jdbc.queryForObject("select name from core.users where id=?", String::class.java, userId))
        mvc.patch("/api/v1/me") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", sessionCookie))
            content = """{"expectedRevision":1,"name":"Stale Name"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error.code") { value("REVISION_CONFLICT") }
            jsonPath("$.error.details.currentRevision") { value(2) }
        }
        mvc.patch("/api/v1/me") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", sessionCookie))
            content = """{"expectedRevision":2,"name":"Forged","role":"admin","userId":"another-user"}"""
        }.andExpect { status { isUnprocessableEntity() } }
        assertEquals("Updated Name", jdbc.queryForObject("select name from core.users where id=?", String::class.java, userId))

        val auditBeforeCoercions = jdbc.queryForObject("select count(*) from core.audit_events where event_type='auth.profile_update'", Int::class.java)
        listOf(
            """{"expectedRevision":2.5,"name":"Fractional Revision"}""",
            """{"expectedRevision":"2","name":"String Revision"}""",
        ).forEach { invalidRevision ->
            mvc.patch("/api/v1/me") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                cookie(Cookie("aja_session", sessionCookie))
                content = invalidRevision
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            }
        }
        assertEquals(2, jdbc.queryForObject("select revision from core.users where id=?", Int::class.java, userId))
        assertEquals("Updated Name", jdbc.queryForObject("select name from core.users where id=?", String::class.java, userId))
        assertEquals(auditBeforeCoercions, jdbc.queryForObject("select count(*) from core.audit_events where event_type='auth.profile_update'", Int::class.java))
    }

    @Test
    fun profileRejectsSessionExpiringWhileWaitingForUserLock() {
        val email = "profile-lock-expiry-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Lock Profile Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val cookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        jdbc.update("update core.sessions set expires_at=? where token_hash=?", java.sql.Timestamp.from(mutableClock.instant().plusMillis(1500)), sha256(cookie))

        val lockConnection = DriverManager.getConnection("${postgres.jdbcUrl}&currentSchema=core", "core", "core-test")
        lockConnection.autoCommit = false
        lockConnection.prepareStatement("select id from core.users where normalized_email=? for update").use { statement ->
            statement.setString(1, email)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit<MvcResult> {
                mvc.patch("/api/v1/me") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", cookie))
                    content = """{"expectedRevision":1,"name":"Too Late"}"""
                }.andReturn()
            }
            awaitLockWait("from core.users")
            mutableClock.advance(Duration.ofSeconds(2))
            lockConnection.commit()
            assertEquals(401, pending.get(10, TimeUnit.SECONDS).response.status)
        } finally {
            mutableClock.advance(Duration.ofSeconds(-2))
            runCatching { lockConnection.rollback() }
            lockConnection.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun internalSessionRequiresServiceCredentialAndReturnsMinimalSession() {
        val email = "internal-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Internal Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val sessionCookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')

        mvc.get("/internal/v1/session") { cookie(Cookie("aja_session", sessionCookie)) }.andExpect {
            status { isForbidden() }
        }
        mvc.get("/internal/v1/session") {
            cookie(Cookie("aja_session", sessionCookie))
            header("X-AJA-Service-Token", "wrong-service-token")
        }.andExpect { status { isForbidden() } }
        mvc.get("/internal/v1/session") {
            cookie(Cookie("aja_session", sessionCookie))
            header("X-AJA-Service-Token", "fixture-questions-core-token-1234567890")
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId") { value(com.jayway.jsonpath.JsonPath.read<String>(login.response.contentAsString, "$.user.id")) }
            jsonPath("$.role") { value("student") }
            jsonPath("$.sessionId") { exists() }
        }.andReturn().also {
            assertFalse(it.response.contentAsString.contains(sessionCookie))
            assertFalse(it.response.contentAsString.contains("fixture-questions-core-token-1234567890"))
            assertFalse(it.response.contentAsString.contains(email))
        }
    }

    @Test
    fun failedLoginClearsAndRevokesOnlyPresentedSession() {
        val email = "failed-login-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Login Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        fun login(): Pair<String, String> {
            val result = mvc.post("/api/v1/auth/login") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = """{"email":"$email","password":"A long test password"}"""
            }.andExpect { status { isOk() } }.andReturn()
            val token = result.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
            val id = com.jayway.jsonpath.JsonPath.read<String>(result.response.contentAsString, "$.sessionId")
            return token to id
        }
        val (oldCookie, oldSessionId) = login()
        val (otherCookie, otherSessionId) = login()
        mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", oldCookie))
            content = """{"email":"$email","password":"wrong password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("INVALID_CREDENTIALS") }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")) }
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from core.sessions where id = ? and revoked_at is not null", Int::class.java, oldSessionId))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.sessions where id = ? and revoked_at is not null", Int::class.java, otherSessionId))
        assertFalse(otherCookie.isBlank())
    }

    @Test
    fun logoutRevokesOnlyCurrentSession() {
        val email = "logout-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Logout Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        fun login(): Pair<String, String> {
            val result = mvc.post("/api/v1/auth/login") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                content = """{"email":"$email","password":"A long test password"}"""
            }.andExpect { status { isOk() } }.andReturn()
            return result.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';') to
                com.jayway.jsonpath.JsonPath.read(result.response.contentAsString, "$.sessionId")
        }
        val (logoutCookie, loggedOutId) = login()
        val (otherCookie, otherId) = login()
        mvc.post("/api/v1/auth/logout") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", logoutCookie))
        }.andExpect {
            status { isNoContent() }
            header { string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")) }
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from core.sessions where id = ? and revoked_at is not null", Int::class.java, loggedOutId))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.sessions where id = ? and revoked_at is not null", Int::class.java, otherId))
        assertFalse(otherCookie.isBlank())
    }

    @Test
    fun failedSessionInsertRollsBackPresentedSessionRotation() {
        val email = "rotation-rollback-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Rotation Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val initial = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$email","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val oldCookie = initial.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        val oldSessionId = com.jayway.jsonpath.JsonPath.read<String>(initial.response.contentAsString, "$.sessionId")
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create or replace function core.reject_session() returns trigger language plpgsql as \$\$ begin raise exception 'forced session insert failure'; end \$\$")
                statement.execute("create trigger reject_session before insert on core.sessions for each row execute function core.reject_session()")
            }
        }
        try {
            mvc.post("/api/v1/auth/login") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                cookie(Cookie("aja_session", oldCookie))
                content = """{"email":"$email","password":"A long test password"}"""
            }.andExpect { status { isInternalServerError() } }
            assertEquals(0, jdbc.queryForObject("select count(*) from core.sessions where id = ? and revoked_at is not null", Int::class.java, oldSessionId))
            assertEquals(1, jdbc.queryForObject("select count(*) from core.sessions where user_id = (select id from core.users where normalized_email = ?) and revoked_at is null", Int::class.java, email))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists reject_session on core.sessions")
                    statement.execute("drop function if exists core.reject_session()")
                }
            }
        }
    }

    @Test
    fun unknownApiPathUsesContractNotFoundEnvelope() {
        mvc.get("/api/v1/auth/does-not-exist").andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("NOT_FOUND") }
            jsonPath("$.error.requestId") { exists() }
        }
    }

    @Test
    fun serviceUsesOwnedSchemaAndRole() {
        assertEquals("core", jdbc.queryForObject("select current_schema()", String::class.java))
        assertEquals("core", jdbc.queryForObject("select current_user", String::class.java))
        listOf("questions", "email").forEach { schema ->
            assertPrivilegeDenied { jdbc.queryForObject("select 1 from $schema.role_access_probe", Int::class.java) }
            assertPrivilegeDenied { jdbc.update("insert into $schema.role_access_probe(id) values (1)") }
            assertPrivilegeDenied { jdbc.execute("create table $schema.forbidden_test(id int)") }
        }
    }

    @Test
    fun migrationsAreRepeatable() {
        flyway.migrate()
        assertEquals(4, jdbc.queryForObject("select count(*) from core.flyway_schema_history where success", Int::class.java))
    }

    private fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    private fun awaitLockWait(queryFragment: String) {
        repeat(100) {
            val blocked = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.prepareStatement(
                    "select exists(select 1 from pg_stat_activity where wait_event_type='Lock' and query not ilike '%pg_stat_activity%')",
                ).use { statement ->
                    statement.executeQuery().use { result -> result.next() && result.getBoolean(1) }
                }
            }
            if (blocked) return
            Thread.sleep(25)
        }
        val active = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select coalesce(string_agg(coalesce(wait_event_type,'none')||':'||left(query,100),' | '),'none') from pg_stat_activity where state='active' and pid<>pg_backend_pid()").use { result ->
                    result.next(); result.getString(1)
                }
            }
        }
        assertTrue(false, "Expected request to wait for a PostgreSQL row lock; active queries: $active")
    }

    private fun latestOutboxPayload(eventType: String): String {
        val event = jdbc.queryForMap("select id, nonce, encrypted_payload from core.outbox_events where event_type = ? order by created_at desc limit 1", eventType)
        val eventId = event["id"] as String
        val nonce = event["nonce"] as ByteArray
        val encryptedPayload = event["encrypted_payload"] as ByteArray
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, SecretKeySpec(ByteArray(32) { 7 }, "AES"), GCMParameterSpec(128, nonce))
        decrypt.updateAAD("$eventId|$eventType|1".toByteArray())
        return String(decrypt.doFinal(encryptedPayload))
    }

    private fun awaitPublished(eventId: String) {
        repeat(100) {
            if (jdbc.queryForObject("select published_at is not null from core.outbox_events where id=?", Boolean::class.java, eventId) == true) return
            Thread.sleep(50)
        }
        assertTrue(false, "Outbox event was not confirmed by RabbitMQ")
    }

    private fun declareEmailQueueTopology() {
        rabbitTemplate.execute { channel ->
            channel.exchangeDeclare("aja.events", "topic", true, false, emptyMap())
            channel.queueDeclare("email.identity.events", true, false, false, mapOf(
                "x-dead-letter-exchange" to "",
                "x-dead-letter-routing-key" to "email.identity.dlq",
            ))
            channel.queueDeclare("email.identity.retry", true, false, false, mapOf(
                "x-message-ttl" to 5000,
                "x-dead-letter-exchange" to "",
                "x-dead-letter-routing-key" to "email.identity.events",
            ))
            channel.queueDeclare("email.identity.dlq", true, false, false, emptyMap())
            channel.queueBind("email.identity.events", "aja.events", "identity.email-confirmation-requested")
            channel.queueBind("email.identity.events", "aja.events", "identity.password-reset-requested")
        }
    }

    private fun receivePublishedEvent(eventId: String): String {
        repeat(40) {
            val message = rabbitTemplate.receive("email.identity.events", 250) ?: return@repeat
            val body = String(message.body)
            if (body.contains(eventId)) return body
        }
        assertTrue(false, "Published event was not present in the durable email queue")
        return ""
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        @Container @JvmField val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        @Container @JvmField val rabbit = RabbitMQContainer("rabbitmq:4-management-alpine")

        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            registry.add("API_ALLOWED_ORIGINS") { "https://aja.example.test" }
            registry.add("core.outbox.encryption-key") { java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
            registry.add("core.outbox.operator-token") { "fixture-outbox-operator-token-1234567890" }
            registry.add("core.internal.questions-token") { "fixture-questions-core-token-1234567890" }
            postgres.start()
            rabbit.start()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE ROLE core LOGIN PASSWORD 'core-test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT")
                    statement.execute("CREATE ROLE questions LOGIN PASSWORD 'questions-test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT")
                    statement.execute("CREATE ROLE email LOGIN PASSWORD 'email-test' NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT")
                    statement.execute("CREATE SCHEMA core AUTHORIZATION core")
                    statement.execute("CREATE SCHEMA questions AUTHORIZATION questions")
                    statement.execute("CREATE SCHEMA email AUTHORIZATION email")
                    statement.execute("REVOKE ALL ON SCHEMA core FROM PUBLIC")
                    statement.execute("REVOKE ALL ON SCHEMA questions FROM PUBLIC")
                    statement.execute("REVOKE ALL ON SCHEMA email FROM PUBLIC")
                    statement.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC")
                    statement.execute("CREATE TABLE questions.role_access_probe(id int)")
                    statement.execute("CREATE TABLE email.role_access_probe(id int)")
                    statement.execute("ALTER ROLE core SET search_path = core")
                    statement.execute("ALTER ROLE questions SET search_path = questions")
                    statement.execute("ALTER ROLE email SET search_path = email")
                }
            }
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&currentSchema=core" }
            registry.add("spring.datasource.username") { "core" }
            registry.add("spring.datasource.password") { "core-test" }
            registry.add("spring.rabbitmq.host") { rabbit.host }
            registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
        }
    }
}
