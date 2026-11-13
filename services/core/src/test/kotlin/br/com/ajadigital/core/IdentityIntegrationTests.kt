package br.com.ajadigital.core
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.MvcResult
import jakarta.servlet.http.Cookie
import java.sql.DriverManager
import java.util.UUID
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class IdentityIntegrationTests : CoreIntegrationTestBase() {
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
        val registration = liveHttp("POST", "/auth/register", null, """{"name":"Confirmation Student","email":"$email","password":"A long test password"}""")
        assertEquals(201, registration.statusCode(), registration.body())
        val originalTokenHash = jdbc.queryForObject("select token_hash from core.auth_tokens where email_at_issue=? and purpose='email_confirmation'", String::class.java, email)!!.trim()
        val beforeEvents = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        val known = liveHttp("POST", "/auth/email-confirmation-requests", null, """{"email":"$email"}""")
        assertEquals(202, known.statusCode(), known.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(known.body()).hasNonNull("message"))
        val unknown = mvc.post("/api/v1/auth/email-confirmation-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"unknown-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        assertEquals(known.body(), unknown.response.contentAsString)
        assertEquals(beforeEvents + 1, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        val payload = latestOutboxPayload("identity.email-confirmation-requested")
        val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        val tokenHash = jdbc.queryForObject(
            "select token_hash from core.auth_tokens where user_id = (select id from core.users where normalized_email = ?) and purpose = 'email_confirmation' and used_at is null",
            String::class.java, email,
        )!!
        assertEquals(sha256(token), tokenHash.trim())
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, originalTokenHash))
        assertFalse(known.body().contains(token))
    }

    @Test
    fun passwordResetRequestsAreNeutralForKnownAndUnknownAddresses() {
        val email = "reset-request-${UUID.randomUUID()}@example.test"
        val registration = liveHttp("POST", "/auth/register", null, """{"name":"Reset Student","email":"$email","password":"A long test password"}""")
        assertEquals(201, registration.statusCode(), registration.body())
        val beforeEvents = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        val known = liveHttp("POST", "/auth/password-reset-requests", null, """{"email":"$email"}""")
        assertEquals(202, known.statusCode(), known.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(known.body()).hasNonNull("message"))
        val unknown = mvc.post("/api/v1/auth/password-reset-requests") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"unknown-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isAccepted() } }.andReturn()
        assertEquals(known.body(), unknown.response.contentAsString)
        assertEquals(beforeEvents + 1, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        val payload = latestOutboxPayload("identity.password-reset-requested")
        val token = Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        val tokenHash = jdbc.queryForObject(
            "select token_hash from core.auth_tokens where user_id = (select id from core.users where normalized_email = ?) and purpose = 'password_reset' and used_at is null",
            String::class.java, email,
        )!!
        assertEquals(sha256(token), tokenHash.trim())
        assertFalse(known.body().contains(token))
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
        val confirmed = liveHttp("POST", "/auth/email-confirmations", null, """{"token":"$confirmationToken"}""")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val confirmedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(confirmed.body())
        assertEquals(confirmedEmail, confirmedBody.get("email").asText())
        assertTrue(confirmedBody.hasNonNull("emailConfirmedAt"))
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

        fun requestReset(): String {
            val response = liveHttp("POST", "/auth/password-reset-requests", null, """{"email":"$email"}""")
            assertEquals(202, response.statusCode(), response.body())
            val payload = latestOutboxPayload("identity.password-reset-requested")
            return Regex(""""token":"([^"]+)"""").find(payload)!!.groupValues[1]
        }
        val previousToken = requestReset()
        val previousHash = sha256(previousToken)
        val resetToken = requestReset()
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue=? and purpose='password_reset' and used_at is null", Int::class.java, email))
        assertEquals(1, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, previousHash))

        val newPassword = "Brand new long password"
        val reset = liveHttp("POST", "/auth/password-resets", null, """{"token":"$resetToken","password":"$newPassword"}""")
        assertEquals(200, reset.statusCode(), reset.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(reset.body()).hasNonNull("message"))
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
        val registration = liveHttp("POST", "/auth/register", null, """{"name":"Login Student","email":"$email","password":"A long test password"}""")
        assertEquals(201, registration.statusCode(), registration.body())
        val result = liveHttp("POST", "/auth/login", null, """{"email":"$email","password":"A long test password"}""")
        assertEquals(200, result.statusCode(), result.body())
        val responseBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.body())
        assertEquals(email, responseBody.at("/user/email").asText())
        assertTrue(responseBody.hasNonNull("sessionId"))
        assertTrue(responseBody.hasNonNull("expiresAt"))
        val setCookie = result.headers().firstValue("Set-Cookie").orElseThrow()
        assertTrue(setCookie.contains("aja_session="))
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("Path=/api/v1"))
        assertTrue(setCookie.contains("SameSite=Lax"))
        assertTrue(setCookie.contains("Max-Age=28800"))
        assertFalse(setCookie.contains("Secure"))
        val sessionCookieValue = setCookie.substringAfter("aja_session=").substringBefore(';')
        val sessionId = responseBody.get("sessionId").asText()
        val storedHash = jdbc.queryForObject("select token_hash from core.sessions where id = ?", String::class.java, sessionId)!!
        assertEquals(MessageDigest.getInstance("SHA-256").digest(sessionCookieValue.toByteArray()).joinToString("") { "%02x".format(it) }, storedHash.trim())
        assertEquals(28800.0, jdbc.queryForObject("select extract(epoch from expires_at - created_at) from core.sessions where id = ?", Double::class.java, sessionId))
        assertFalse(result.body().contains(sessionCookieValue))
        val restoredSession = liveHttp("GET", "/auth/session", sessionCookieValue)
        assertEquals(200, restoredSession.statusCode(), restoredSession.body())
        assertEquals(sessionId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(restoredSession.body()).get("sessionId").asText())
        assertEquals(email, tools.jackson.databind.json.JsonMapper.builder().build().readTree(restoredSession.body()).at("/user/email").asText())
        val self = liveHttp("GET", "/me", sessionCookieValue)
        assertEquals(200, self.statusCode(), self.body())
        assertEquals(email, tools.jackson.databind.json.JsonMapper.builder().build().readTree(self.body()).get("email").asText())
        jdbc.update("update core.users set active=false where normalized_email=?", email)
        val revokedSession = liveHttp("GET", "/me", sessionCookieValue)
        assertEquals(401, revokedSession.statusCode(), revokedSession.body())
        assertEquals("SESSION_REQUIRED", tools.jackson.databind.json.JsonMapper.builder().build().readTree(revokedSession.body()).at("/error/code").asText())
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
        val login = liveHttp("POST", "/auth/login", null, """{"email":"$email","password":"A long test password"}""")
        assertEquals(200, login.statusCode(), login.body())
        val sessionCookie = login.headers().firstValue("Set-Cookie").orElseThrow().substringAfter("aja_session=").substringBefore(';')
        val userId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(login.body()).at("/user/id").asText()
        val profile = liveHttp("PATCH", "/me", sessionCookie, """{"expectedRevision":1,"name":"Updated Name"}""")
        assertEquals(200, profile.statusCode(), profile.body())
        val profileBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(profile.body())
        assertEquals("Updated Name", profileBody.get("name").asText())
        assertEquals(2, profileBody.get("revision").asInt())
        assertEquals(userId, profileBody.get("updatedBy").asText())
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
}
