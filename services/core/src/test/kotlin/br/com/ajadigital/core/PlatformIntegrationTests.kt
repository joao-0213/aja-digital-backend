package br.com.ajadigital.core
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import java.util.UUID

class PlatformIntegrationTests : CoreIntegrationTestBase() {
    @Autowired protected lateinit var transactionRunner: TransactionRunner
    @Test
    fun publicHealthIsAvailableWithoutASession() {
        val response = liveHttp("GET", "/health", "")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("ok", tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).get("status").asText())
    }

    @Test
    fun everyCoreOpenApiOperationRejectsItsUnauthenticatedOrInvalidProbe() {
        val failures = mutableListOf<String>()
        openApiCoverage.expectedDenials().forEach { probe ->
            val origin = if (probe.expectedStatus == 403) "https://denied.example.test" else "https://aja.example.test"
            val key = if (probe.idempotencyKey) "b16-${UUID.randomUUID()}" else null
            val response = liveHttp(probe.method, probe.target, "", probe.body, key, origin, probe.contentType)
            openApiCoverage.recordProbeResponse(probe.method, probe.target, response.statusCode())
            if (response.statusCode() != probe.expectedStatus) failures += "${probe.method} ${probe.target} (${probe.id}) expected ${probe.expectedStatus}, got ${response.statusCode()}: ${response.body()}"
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
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
        assertEquals(16, jdbc.queryForObject("select count(*) from core.flyway_schema_history where success", Int::class.java))
    }

    @Test
    fun springTransactionRunnerRollsBackAtomicallyOnFailure() {
        val rolledBackId = UUID.randomUUID().toString()
        assertThrows(IllegalStateException::class.java) {
            transactionRunner.run {
                jdbc.update(
                    "INSERT INTO core.audit_events(id, event_type, outcome, subject_user_id) VALUES (?, 'auth.login', 'success', NULL)",
                    rolledBackId,
                )
                throw IllegalStateException("force rollback")
            }
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE id = ?", Int::class.java, rolledBackId))

        val committedId = UUID.randomUUID().toString()
        transactionRunner.run {
            jdbc.update(
                "INSERT INTO core.audit_events(id, event_type, outcome, subject_user_id) VALUES (?, 'auth.login', 'success', NULL)",
                committedId,
            )
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE id = ?", Int::class.java, committedId))
    }
}
