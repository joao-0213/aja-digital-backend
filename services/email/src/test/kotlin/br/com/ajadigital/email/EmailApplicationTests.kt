package br.com.ajadigital.email

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import com.rabbitmq.client.AMQP
import br.com.ajadigital.email.adapter.`in`.messaging.EmailDeliveryOperationsController
import br.com.ajadigital.email.application.delivery.RedriveDeadEmail
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import br.com.ajadigital.email.ports.out.DeliveryFailure
import br.com.ajadigital.email.ports.out.EmailSender
import org.springframework.boot.test.context.TestConfiguration
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import br.com.ajadigital.email.adapter.out.messaging.RedriveConfirmationHook
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Testcontainers
@SpringBootTest
@Import(EmailApplicationTests.CaptureSenderConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailApplicationTests @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val flyway: Flyway,
    private val rabbitTemplate: RabbitTemplate,
    private val sender: CapturingEmailSender,
    private val operations: EmailDeliveryOperationsController,
    private val redriveDeadEmail: RedriveDeadEmail,
    private val confirmationHook: CapturingRedriveConfirmationHook,
    private val listenerRegistry: RabbitListenerEndpointRegistry,
) {
    @Test
    fun `email operator rejects blank configured secret`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmailDeliveryOperationsController(redriveDeadEmail, " \t")
        }
        assertEquals(401, operations.redrive(java.util.UUID.randomUUID().toString(), " \t").statusCode.value())
    }

    @Test
    fun serviceUsesOwnedSchemaAndRole() {
        assertEquals("email", jdbc.queryForObject("select current_schema()", String::class.java))
        assertEquals("email", jdbc.queryForObject("select current_user", String::class.java))
        listOf("core", "questions").forEach { schema ->
            assertPrivilegeDenied { jdbc.queryForObject("select 1 from $schema.role_access_probe", Int::class.java) }
            assertPrivilegeDenied { jdbc.update("insert into $schema.role_access_probe(id) values (1)") }
            assertPrivilegeDenied { jdbc.execute("create table $schema.forbidden_test(id int)") }
        }
    }

    @Test
    fun migrationsAreRepeatable() {
        flyway.migrate()
        assertEquals(3, jdbc.queryForObject("select count(*) from email.flyway_schema_history where success", Int::class.java))
    }

    @Test
    fun `confirmed event is sent once and duplicate is acknowledged`() {
        val eventId = java.util.UUID.randomUUID().toString()
        val event = encryptedEvent(eventId, "identity.email-confirmation-requested", "confirm-token")
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", event)
        waitForStatus(eventId, "sent")
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", event)
        Thread.sleep(300)

        assertEquals(1, sender.messages.count { it.eventId == eventId })
        assertTrue(sender.messages.any { it == CapturedEmail(eventId, "worker@example.test", "confirm-token", "email_confirmation") })
        assertEquals("sent", jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
    }

    @Test
    fun `temporary provider failure retries and then acknowledges`() {
        val eventId = java.util.UUID.randomUUID().toString()
        sender.failures[eventId] = DeliveryFailure(ambiguous = false)
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", encryptedEvent(eventId, "identity.email-confirmation-requested", "retry-token"))

        waitForStatus(eventId, "sent")
        assertEquals(2, jdbc.queryForObject("select attempt_count from email.delivery_events where event_id=?", Int::class.java, eventId))
        assertEquals(2, sender.messages.count { it.eventId == eventId })
    }

    @Test
    fun `ambiguous provider outcome is terminal and duplicate does not resend`() {
        val eventId = java.util.UUID.randomUUID().toString()
        sender.failures[eventId] = DeliveryFailure(ambiguous = true)
        val event = encryptedEvent(eventId, "identity.password-reset-requested", "reset-token")
        rabbitTemplate.convertAndSend("aja.events", "identity.password-reset-requested", event)
        waitForStatus(eventId, "unknown")
        rabbitTemplate.convertAndSend("aja.events", "identity.password-reset-requested", event)
        Thread.sleep(300)

        assertEquals(1, sender.messages.count { it.eventId == eventId })
        assertTrue(sender.messages.any { it == CapturedEmail(eventId, "worker@example.test", "reset-token", "password_reset") })
        assertEquals("unknown", jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
    }

    @Test
    fun `invalid encrypted event is recorded as poison and moved to dlq`() {
        val eventId = java.util.UUID.randomUUID().toString()
        val poison = ObjectMapper().writeValueAsBytes(mapOf("id" to eventId, "eventType" to "identity.email-confirmation-requested", "version" to 1))
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", poison)

        waitForStatus(eventId, "dead")
        for (attempt in 0 until 100) {
            if ((rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0) > 0) break
            Thread.sleep(50)
        }
        assertTrue((rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0) > 0)
    }

    @Test
    fun `malformed duplicate cannot downgrade sent delivery`() {
        val eventId = java.util.UUID.randomUUID().toString()
        val event = encryptedEvent(eventId, "identity.email-confirmation-requested", "collision-token")
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", event)
        waitForStatus(eventId, "sent")
        val poison = ObjectMapper().writeValueAsBytes(mapOf("id" to eventId, "eventType" to "unknown"))
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", poison)
        repeat(100) {
            if ((rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0) > 0) return@repeat
            Thread.sleep(50)
        }

        assertEquals("sent", jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
        assertEquals(1, sender.messages.count { it.eventId == eventId })
    }

    @Test
    fun `operator can redrive a dead encrypted event after provider recovery`() {
        val eventId = java.util.UUID.randomUUID().toString()
        val event = encryptedEvent(eventId, "identity.email-confirmation-requested", "redrive-token")
        val dlqCountBefore = rabbitTemplate.execute { it.messageCount("email.identity.dlq") }
        sender.failCounts[eventId] = AtomicInteger(5)
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", event)
        waitForStatus(eventId, "dead")

        val denied = operations.redrive(eventId, "wrong").statusCode.value()
        assertEquals(401, denied)
        val accepted = operations.redrive(eventId, "b03-operator-test-secret").statusCode.value()
        assertEquals(202, accepted)
        waitForStatus(eventId, "sent")

        assertEquals(1, sender.delivered.count { it.eventId == eventId })
        assertTrue(sender.delivered.any { it == CapturedEmail(eventId, "worker@example.test", "redrive-token", "email_confirmation") })
        assertEquals(1, jdbc.queryForObject("select attempt_count from email.delivery_events where event_id=?", Int::class.java, eventId))
        assertEquals(dlqCountBefore, rabbitTemplate.execute { it.messageCount("email.identity.dlq") })
    }

    @Test
    fun `redelivered dead event returns to dlq and can then be redriven`() {
        val dlqCountBefore = rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0L
        val unrelatedId = java.util.UUID.randomUUID().toString()
        val unrelatedPoison = ObjectMapper().writeValueAsBytes(mapOf("id" to unrelatedId, "eventType" to "unknown"))
        val unrelatedProperties = AMQP.BasicProperties.Builder()
            .deliveryMode(2)
            .contentType("application/json")
            .correlationId("b03-unrelated-correlation")
            .headers(mapOf(
                "b03-retained-marker" to "retain-unrelated-properties",
                "x-death" to listOf(mapOf(
                    "count" to 1L,
                    "reason" to "rejected",
                    "queue" to "email.identity.events",
                    "exchange" to "aja.events",
                    "routing-keys" to listOf("identity.email-confirmation-requested"),
                )),
            ))
            .build()
        rabbitTemplate.execute { channel -> channel.basicPublish("", "email.identity.dlq", false, unrelatedProperties, unrelatedPoison) }
        waitForDlqCount(dlqCountBefore + 1L)

        val eventId = java.util.UUID.randomUUID().toString()
        val event = encryptedEvent(eventId, "identity.email-confirmation-requested", "dead-redrive-token")
        jdbc.update(
            "insert into email.delivery_events(event_id,event_type,status,attempt_count) values(?,?,'dead',5)",
            eventId, "identity.email-confirmation-requested",
        )

        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", event)
        waitForDlqCount(dlqCountBefore + 2L)

        assertEquals("dead", jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
        assertEquals(202, operations.redrive(eventId, "b03-operator-test-secret").statusCode.value())
        waitForStatus(eventId, "sent")

        assertEquals(1, sender.delivered.count { it.eventId == eventId })
        assertTrue(sender.delivered.any { it == CapturedEmail(eventId, "worker@example.test", "dead-redrive-token", "email_confirmation") })
        assertEquals(dlqCountBefore + 1L, rabbitTemplate.execute { it.messageCount("email.identity.dlq") })
        val preservedProperties = readDlqProperties(unrelatedPoison)
        assertEquals("b03-unrelated-correlation", preservedProperties?.correlationId)
        assertEquals("retain-unrelated-properties", preservedProperties?.headers?.get("b03-retained-marker")?.toString())
        assertXDeathHeader(preservedProperties)
    }

    @Test
    fun `confirmed redrive before ack can be retried without duplicate send`() {
        val containers = listenerRegistry.listenerContainers.toList()
        val eventId = java.util.UUID.randomUUID().toString()
        containers.forEach { it.stop() }
        try {
            val eventType = "identity.email-confirmation-requested"
            val event = encryptedEvent(eventId, eventType, "confirm-crash-token")
            val originalProperties = AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .contentType("application/json")
                .correlationId("b03-target-correlation")
                .headers(mapOf("x-death" to listOf(mapOf(
                    "count" to 1L,
                    "reason" to "rejected",
                    "queue" to "email.identity.events",
                    "exchange" to "aja.events",
                    "routing-keys" to listOf(eventType),
                ))))
                .build()
            jdbc.update(
                "insert into email.delivery_events(event_id,event_type,status,attempt_count) values(?,?,'dead',5)",
                eventId, eventType,
            )
            rabbitTemplate.execute { channel -> channel.basicPublish("", "email.identity.dlq", false, originalProperties, event) }
            confirmationHook.failOnceFor.set(eventId)

            assertThrows(Exception::class.java) {
                operations.redrive(eventId, "b03-operator-test-secret")
            }
            assertEquals("dead", jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
            assertEquals(202, operations.redrive(eventId, "b03-operator-test-secret").statusCode.value())
            assertEquals("b03-target-correlation", confirmationHook.confirmedProperties[eventId]?.correlationId)
            assertXDeathHeader(confirmationHook.confirmedProperties[eventId])
        } finally {
            containers.forEach { it.start() }
        }
        waitForStatus(eventId, "sent")
        Thread.sleep(300)
        assertEquals(1, sender.delivered.count { it.eventId == eventId })
    }

    @Test
    fun `consumer restart recovery treats persisted sending state as ambiguous`() {
        val eventId = java.util.UUID.randomUUID().toString()
        // This is the durable state left by a process death after it claimed the event but before saving the provider result.
        jdbc.update("insert into email.delivery_events(event_id,event_type,status,attempt_count) values(?,?,'sending',1)", eventId, "identity.email-confirmation-requested")
        rabbitTemplate.convertAndSend("aja.events", "identity.email-confirmation-requested", encryptedEvent(eventId, "identity.email-confirmation-requested", "crash-token"))

        waitForStatus(eventId, "unknown")
        assertEquals(0, sender.messages.count { it.eventId == eventId })
    }

    private fun waitForStatus(eventId: String, expected: String) {
        repeat(100) {
            if (jdbc.query("select status from email.delivery_events where event_id=?", { rs, _ -> rs.getString(1) }, eventId).firstOrNull() == expected) return
            Thread.sleep(50)
        }
        assertEquals(expected, jdbc.queryForObject("select status from email.delivery_events where event_id=?", String::class.java, eventId))
    }

    private fun waitForDlqCount(expected: Long) {
        repeat(100) {
            if ((rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0) >= expected) return
            Thread.sleep(50)
        }
        assertTrue((rabbitTemplate.execute { it.messageCount("email.identity.dlq") } ?: 0) >= expected)
    }

    private fun readDlqProperties(body: ByteArray): AMQP.BasicProperties? = rabbitTemplate.execute { channel ->
        val queue = "email.identity.dlq"
        val count = channel.messageCount(queue).toInt()
        val held = mutableListOf<com.rabbitmq.client.GetResponse>()
        repeat(count) { channel.basicGet(queue, false)?.let(held::add) }
        var found: AMQP.BasicProperties? = null
        held.forEach { response ->
            if (found == null && response.body.contentEquals(body)) {
                found = response.props
                channel.basicAck(response.envelope.deliveryTag, false)
            } else {
                channel.basicNack(response.envelope.deliveryTag, false, true)
            }
        }
        found
    }

    private fun assertXDeathHeader(properties: AMQP.BasicProperties?) {
        val records = properties?.headers?.get("x-death") as? List<*> ?: throw AssertionError("x-death header was not preserved")
        assertEquals(1, records.size)
        val record = records.single() as? Map<*, *> ?: throw AssertionError("x-death record was not preserved")
        assertEquals("1", record["count"].toString())
        assertEquals("rejected", record["reason"].toString())
        assertEquals("email.identity.events", record["queue"].toString())
        val routingKeys = record["routing-keys"] as? List<*> ?: throw AssertionError("x-death routing keys were not preserved")
        assertEquals(listOf("identity.email-confirmation-requested"), routingKeys.map { it.toString() })
    }

    private fun encryptedEvent(eventId: String, eventType: String, token: String): ByteArray {
        val nonce = ByteArray(12) { 3 }
        val key = ByteArray(32) { 7 }
        val payload = ObjectMapper().writeValueAsBytes(
            mapOf(
                "recipient" to "worker@example.test", "token" to token, "userId" to "user-1",
                "purpose" to if (eventType == "identity.email-confirmation-requested") "email_confirmation" else "password_reset",
            ),
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$eventId|$eventType|1".toByteArray())
        val ciphertext = cipher.doFinal(payload)
        val keyId = MessageDigest.getInstance("SHA-256").digest(key).take(8).joinToString("") { "%02x".format(it) }
        return ObjectMapper().writeValueAsBytes(
            mapOf(
                "id" to eventId,
                "eventType" to eventType,
                "version" to 1,
                "keyId" to keyId,
                "nonce" to Base64.getEncoder().encodeToString(nonce),
                "ciphertext" to Base64.getEncoder().encodeToString(ciphertext),
            ),
        )
    }

    @TestConfiguration
    class CaptureSenderConfiguration {
        @Bean @Primary fun capturedEmailSender() = CapturingEmailSender()
        @Bean @Primary fun redriveConfirmationHook() = CapturingRedriveConfirmationHook()
    }

    private fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    companion object {
        @Container @JvmField val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        @Container @JvmField val rabbit = RabbitMQContainer("rabbitmq:4-management-alpine")

        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
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
                    statement.execute("CREATE TABLE core.role_access_probe(id int)")
                    statement.execute("ALTER ROLE core SET search_path = core")
                    statement.execute("ALTER ROLE questions SET search_path = questions")
                    statement.execute("ALTER ROLE email SET search_path = email")
                }
            }
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&currentSchema=email" }
            registry.add("spring.datasource.username") { "email" }
            registry.add("spring.datasource.password") { "email-test" }
            registry.add("spring.rabbitmq.host") { rabbit.host }
            registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
            registry.add("email.outbox.encryption-key") { Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
            registry.add("email.provider.url") { "http://127.0.0.1:9/email" }
            registry.add("email.retry.delay-ms") { "50" }
            registry.add("email.delivery.operator-token") { "b03-operator-test-secret" }
        }
    }
}

data class CapturedEmail(val eventId: String, val recipient: String, val token: String, val purpose: String)

class CapturingEmailSender : EmailSender {
    val messages = CopyOnWriteArrayList<CapturedEmail>()
    val delivered = CopyOnWriteArrayList<CapturedEmail>()
    val failures = ConcurrentHashMap<String, DeliveryFailure>()
    val failCounts = ConcurrentHashMap<String, AtomicInteger>()
    override fun send(eventId: String, recipient: String, token: String, purpose: String) {
        messages += CapturedEmail(eventId, recipient, token, purpose)
        failures.remove(eventId)?.let { throw it }
        val remaining = failCounts[eventId]
        if (remaining != null && remaining.getAndUpdate { value -> (value - 1).coerceAtLeast(0) } > 0) {
            throw DeliveryFailure(ambiguous = false)
        }
        delivered += CapturedEmail(eventId, recipient, token, purpose)
    }
}

class CapturingRedriveConfirmationHook : RedriveConfirmationHook {
    val confirmedProperties = ConcurrentHashMap<String, AMQP.BasicProperties>()
    val failOnceFor = AtomicReference<String?>(null)

    override fun afterConfirmed(eventId: String, originalProperties: AMQP.BasicProperties, channel: com.rabbitmq.client.Channel) {
        confirmedProperties[eventId] = originalProperties
        if (failOnceFor.compareAndSet(eventId, null)) {
            channel.connection.close()
            throw IllegalStateException("simulated process loss after publisher confirmation")
        }
    }
}
