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
                val validSession = cookie == "aja_session=${"A".repeat(43)}"
                val status = when {
                    !validToken -> 403
                    cookie == "aja_session=${"C".repeat(43)}" -> 200
                    !validSession -> 401
                    else -> 200
                }
                val role = if (cookie == "aja_session=${"C".repeat(43)}") "observer" else "student"
                val body = if (status == 200) """{"userId":"test-user","sessionId":"test-session","role":"$role","active":true,"emailConfirmedAt":null}""" else "{}"
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
