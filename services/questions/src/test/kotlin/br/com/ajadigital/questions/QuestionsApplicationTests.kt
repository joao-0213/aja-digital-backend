package br.com.ajadigital.questions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestClient
import org.springframework.http.MediaType
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionsApplicationTests @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val flyway: Flyway,
    @LocalServerPort private val port: Int,
) {
    @Test
    fun `question list requires an active session`() {
        val response = RestClient.create().get().uri("http://localhost:$port/api/v1/questions")
            .exchange { _, result -> result.statusCode.value() to result.body.readAllBytes().decodeToString() }
        assertEquals(401, response.first)
        assertTrue(response.second.contains("SESSION_REQUIRED"))
    }

    @Test
    fun `expired or revoked Core session is not accepted`() {
        val response = get("/questions", "B".repeat(43))
        assertEquals(401, response.first)
        assertTrue(response.second.contains("SESSION_REQUIRED"))
    }

    @Test
    fun `question reads reject a Core principal outside the contract roles`() {
        val response = get("/questions", "C".repeat(43))
        assertEquals(403, response.first)
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `public question list filters and paginates without exposing the answer`() {
        val response = get("/questions?subject=Matemática&difficulty=easy&institution=AJA&year=1&page=1&pageSize=1&sort=-id")
        assertEquals(200, response.first)
        assertTrue(response.second.contains("q2"), response.second)
        assertTrue(response.second.contains("\"pageSize\":1"))
        assertTrue(response.second.contains("\"total\":2"))
        assertFalse(response.second.contains("correctOption"))
        assertFalse(response.second.contains("explanation"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `query search ignores diacritics`() {
        val response = get("/questions?q=perimetro")
        assertEquals(200, response.first)
        assertTrue(response.second.contains("q2"))
        assertTrue(response.second.contains("\"total\":1"))
    }

    @Test
    fun `unknown list query parameters are rejected after session validation`() {
        val authenticated = get("/questions?unexpected=1")
        assertEquals(422, authenticated.first)

        val unauthenticated = RestClient.create().get().uri("http://localhost:$port/api/v1/questions?unexpected=1")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(401, unauthenticated)
    }

    @Test
    fun `unknown detail query parameters are rejected after session validation`() {
        val authenticated = get("/questions/q1?unexpected=1")
        assertEquals(422, authenticated.first)

        val unauthenticated = RestClient.create().get().uri("http://localhost:$port/api/v1/questions/q1?unexpected=1")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(401, unauthenticated)
    }

    @Test
    fun `invalid pagination uses the contract validation status`() {
        val response = get("/questions?pageSize=101")
        assertEquals(422, response.first)
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `public question detail resolves only the Core image path`() {
        val response = get("/questions/q-image")
        assertEquals(200, response.first)
        assertTrue(response.second.contains("http://localhost:8081/api/v1/files/file-question-image-v1/content"))
        assertFalse(response.second.contains("correctOption"))
        assertFalse(response.second.contains("explanation"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `withdrawn questions are hidden from public detail`() {
        val response = get("/questions/q-withdrawn")
        assertEquals(404, response.first)
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `practice returns current correction only after Core allows it`() {
        val response = postPractice("q1", 1)
        assertEquals(200, response.first, response.second)
        assertTrue(response.second.contains("\"questionId\":\"q1\""))
        assertTrue(response.second.contains("\"choice\":1"))
        assertTrue(response.second.contains("\"correct\":1"))
        assertTrue(response.second.contains("\"isCorrect\":true"))
        assertTrue(response.second.contains("\"explanation\":\"Subtraia 3 e divida por 2.\""))
        jdbc.update("UPDATE questions.question_bank SET correct_option=0, explanation='Updated explanation' WHERE id='q1'")
        val updated = postPractice("q1", 1)
        assertEquals(200, updated.first)
        assertTrue(updated.second.contains("\"correct\":0"))
        assertTrue(updated.second.contains("\"isCorrect\":false"))
        assertTrue(updated.second.contains("Updated explanation"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `practice rechecks question revision after the Core decision`() {
        val response = postPractice("q1", 1, "G".repeat(43))
        assertEquals(200, response.first)
        assertTrue(response.second.contains("\"correct\":0"))
        assertTrue(response.second.contains("\"isCorrect\":false"))
        assertTrue(response.second.contains("Changed during Core"))
        val continuouslyChanging = postPractice("q1", 1, "H".repeat(43))
        assertEquals(409, continuouslyChanging.first)
        assertFalse(continuouslyChanging.second.contains("\"correct\""))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `practice preserves validation precedence`() {
        assertEquals(401, postPractice("q1", 999, "B".repeat(43)).first)
        assertEquals(404, postPractice("q-withdrawn", 999).first)
        assertEquals(422, postPractice("q1", 999).first)
        assertEquals(422, postPractice("q1", 999, "D".repeat(43)).first)
        assertEquals(422, postPractice("q1", 1, query = "?unexpected=1").first)
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `practice does not return a correction when Core reports a live attempt`() {
        val response = postPractice("q1", 1, "D".repeat(43))
        assertEquals(409, response.first)
        assertFalse(response.second.contains("\"correct\""))
        assertFalse(response.second.contains("Subtraia 3"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `practice reports Core outage without a correction`() {
        val response = postPractice("q1", 1, "F".repeat(43))
        assertEquals(503, response.first)
        assertFalse(response.second.contains("\"correct\""))
        assertFalse(response.second.contains("Subtraia 3"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `private snapshot batch is authenticated and preserves image and version`() {
        val client = RestClient.create()
        val denied = client.post().uri("http://localhost:$port/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "invalid")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q-image"]}""")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(403, denied)

        val response = client.post().uri("http://localhost:$port/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "questions-core-test-token-0123456789")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q-image"]}""")
            .exchange { _, result -> result.statusCode.value() to result.body.readAllBytes().decodeToString() }
        assertEquals(200, response.first)
        assertTrue(response.second.contains("\"revision\":2"))
        assertTrue(response.second.contains("\"contentHash\":"))
        assertTrue(response.second.contains("\"fileId\":\"file-question-image-v1\""))
        assertTrue(response.second.contains("\"correctOption\":1"))
        val repeated = client.post().uri("http://localhost:$port/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "questions-core-test-token-0123456789")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q-image"]}""")
            .exchange { _, result -> result.body.readAllBytes().decodeToString() }
        val hash = Regex("\"contentHash\":\"([^\"]+)\"").find(response.second)?.groupValues?.get(1)
        assertTrue(hash != null && repeated.contains("\"contentHash\":\"$hash\""))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `private snapshot rejects a batch with a withdrawn question atomically`() {
        val response = RestClient.create().post().uri("http://localhost:$port/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "questions-core-test-token-0123456789")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q1","q-withdrawn"]}""")
            .exchange { _, result -> result.statusCode.value() to result.body.readAllBytes().decodeToString() }
        assertEquals(404, response.first)
        assertFalse(response.second.contains("q1"))
    }

    @Test
    @Sql("/question-catalog-v1.sql")
    fun `private snapshot rejects duplicate ids and stays outside the public API`() {
        val client = RestClient.create()
        val duplicate = client.post().uri("http://localhost:$port/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "questions-core-test-token-0123456789")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q1","q1"]}""")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(422, duplicate)
        val publicPath = client.post().uri("http://localhost:$port/api/v1/internal/v1/snapshots")
            .header("X-AJA-Service-Token", "questions-core-test-token-0123456789")
            .contentType(MediaType.APPLICATION_JSON).body("""{"ids":["q1"]}""")
            .exchange { _, response -> response.statusCode.value() }
        assertEquals(404, publicPath)
    }

    private fun get(path: String, cookie: String = "A".repeat(43)): Pair<Int, String> = RestClient.create().get()
        .uri("http://localhost:$port/api/v1$path")
        .header("Cookie", "aja_session=$cookie")
        .exchange { _, response -> response.statusCode.value() to response.body.readAllBytes().decodeToString() }

    private fun postPractice(id: String, choice: Int, cookie: String = "A".repeat(43), query: String = ""): Pair<Int, String> = RestClient.create().post()
        .uri("http://localhost:$port/api/v1/questions/$id/practice$query")
        .header("Cookie", "aja_session=$cookie")
        .contentType(MediaType.APPLICATION_JSON).body("""{"choice":$choice}""")
        .exchange { _, response -> response.statusCode.value() to response.body.readAllBytes().decodeToString() }

    @Test
    fun serviceUsesOwnedSchemaAndRole() {
        assertEquals("questions", jdbc.queryForObject("select current_schema()", String::class.java))
        assertEquals("questions", jdbc.queryForObject("select current_user", String::class.java))
        listOf("core", "email").forEach { schema ->
            assertPrivilegeDenied { jdbc.queryForObject("select 1 from $schema.role_access_probe", Int::class.java) }
            assertPrivilegeDenied { jdbc.update("insert into $schema.role_access_probe(id) values (1)") }
            assertPrivilegeDenied { jdbc.execute("create table $schema.forbidden_test(id int)") }
        }
    }

    @Test
    fun migrationsAreRepeatable() {
        flyway.migrate()
        assertEquals(2, jdbc.queryForObject("select count(*) from questions.flyway_schema_history where success", Int::class.java))
    }

    private fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    companion object {
        @Container @JvmField val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        private val coreSessionServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/internal/v1/session") { request ->
                val validToken = request.requestHeaders.getFirst("X-AJA-Service-Token") == "questions-core-test-token-0123456789"
                val cookie = request.requestHeaders.getFirst("Cookie")
                val validSession = cookie in setOf("aja_session=${"A".repeat(43)}", "aja_session=${"D".repeat(43)}", "aja_session=${"F".repeat(43)}", "aja_session=${"G".repeat(43)}", "aja_session=${"H".repeat(43)}")
                val status = when {
                    !validToken -> 403
                    request.requestMethod == "POST" && cookie == "aja_session=${"F".repeat(43)}" -> 503
                    cookie == "aja_session=${"C".repeat(43)}" -> 200
                    !validSession -> 401
                    else -> 200
                }
                val role = if (cookie == "aja_session=${"C".repeat(43)}") "observer" else "student"
                val practice = request.requestMethod == "POST"
                if (practice && cookie in setOf("aja_session=${"G".repeat(43)}", "aja_session=${"H".repeat(43)}")) {
                    DriverManager.getConnection("${postgres.jdbcUrl}&currentSchema=questions", "questions", "questions-test").use { connection ->
                        val revisionCondition = if (cookie == "aja_session=${"G".repeat(43)}") "revision=1" else "true"
                        connection.createStatement().use { it.execute("UPDATE questions.question_bank SET revision=revision+1,correct_option=0,explanation='Changed during Core' WHERE id='q1' AND $revisionCondition") }
                    }
                }
                val body = if (status == 200 && practice) """{"blocked":${cookie == "aja_session=${"D".repeat(43)}"}}"""
                    else if (status == 200) """{"userId":"test-user","sessionId":"test-session","role":"$role","active":true,"emailConfirmedAt":null}""" else "{}"
                request.sendResponseHeaders(status, body.toByteArray().size.toLong())
                request.responseBody.use { it.write(body.toByteArray()) }
            }
            start()
        }

        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            postgres.start()
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
                    statement.execute("CREATE TABLE email.role_access_probe(id int)")
                    statement.execute("CREATE TABLE core.role_access_probe(id int)")
                    statement.execute("ALTER ROLE core SET search_path = core")
                    statement.execute("ALTER ROLE questions SET search_path = questions")
                    statement.execute("ALTER ROLE email SET search_path = email")
                }
            }
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&currentSchema=questions" }
            registry.add("spring.datasource.username") { "questions" }
            registry.add("spring.datasource.password") { "questions-test" }
            registry.add("questions.core.internal-session-url") { "http://127.0.0.1:${coreSessionServer.address.port}/internal/v1/session" }
            registry.add("questions.core.service-token") { "questions-core-test-token-0123456789" }
            registry.add("questions.core.public-api-url") { "http://localhost:8081/api/v1" }
        }

        @AfterAll @JvmStatic fun stopCoreSessionServer() = coreSessionServer.stop(0)
    }
}
