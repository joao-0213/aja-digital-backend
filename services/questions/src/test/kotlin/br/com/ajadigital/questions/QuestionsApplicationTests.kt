package br.com.ajadigital.questions

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

@Testcontainers
@SpringBootTest
class QuestionsApplicationTests @Autowired constructor(private val jdbc: JdbcTemplate, private val flyway: Flyway) {
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
        assertEquals(1, jdbc.queryForObject("select count(*) from questions.flyway_schema_history where success", Int::class.java))
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
            registry.add("spring.rabbitmq.host") { rabbit.host }
            registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
        }
    }
}
