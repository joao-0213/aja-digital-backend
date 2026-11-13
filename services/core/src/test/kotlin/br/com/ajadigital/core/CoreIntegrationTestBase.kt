package br.com.ajadigital.core
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import jakarta.servlet.http.Cookie
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.Instant
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.context.annotation.Import
import br.com.ajadigital.core.adapter.out.messaging.OutboxPublisher
import br.com.ajadigital.core.application.academic.ManageAcademics
import br.com.ajadigital.core.ports.out.IdempotencyStore

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@Import(MutableTestClockConfiguration::class, EmailQueueTopologyTestConfiguration::class, AssessmentSnapshotTestConfiguration::class, CoreOpenApiCoverageConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class CoreIntegrationTestBase {
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var flyway: Flyway
    @Autowired protected lateinit var mvc: MockMvc
    @Autowired protected lateinit var rabbitTemplate: RabbitTemplate
    @Autowired protected lateinit var outboxPublisher: OutboxPublisher
    @Autowired protected lateinit var passwordEncoder: PasswordEncoder
    @Autowired protected lateinit var mutableClock: MutableTestClock
    @Autowired protected lateinit var manageAcademics: ManageAcademics
    @Autowired protected lateinit var idempotencyStore: IdempotencyStore
    @Autowired protected lateinit var questionSnapshotProvider: TestQuestionSnapshotProvider
    @Autowired protected lateinit var webContext: ServletWebServerApplicationContext
    @Autowired protected lateinit var openApiCoverage: CoreOpenApiCoverageFilter

    @AfterAll
    fun everyCoreOpenApiOperationHasHttpSuccessAndExpectedDenial() {
        if (System.getenv("B16_OPENAPI_COVERAGE") != "true") return
        val remaining = pendingCoverageClasses.updateAndGet { if (it < 0) featureClassCount() - 1 else it - 1 }
        if (remaining <= 0) CoreOpenApiCoverageFilter.assertEveryOperationHasSuccessAndExpectedDenial()
    }

    @BeforeEach
    fun resetClock() = mutableClock.reset()

    protected fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    protected fun liveHttp(method: String, path: String, cookie: String?, body: String? = null, key: String? = null, origin: String = "https://aja.example.test", contentType: String = "application/json"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${checkNotNull(webContext.webServer).port}/api/v1$path"))
            .header("Origin", origin)
            .header("Content-Type", contentType)
        cookie?.let { builder.header("Cookie", "aja_session=$it") }
        key?.let { builder.header("Idempotency-Key", it) }
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    protected data class CoreChild(val process: Process, val port: Int)

    protected data class QuestionsChild(val process: Process, val port: Int)

    protected fun startQuestionsProcess(coreInternalSessionUrl: String? = null): QuestionsChild {
        val port = ServerSocket(0).use { it.localPort }
        val jar = Path.of("..", "questions", "build", "libs", "questions-0.0.1-SNAPSHOT.jar").toAbsolutePath()
        assertTrue(Files.isRegularFile(jar), "Questions boot jar missing at $jar; build services/questions bootJar first")
        val processLog = Path.of("build", "questions-process-$port.log").toAbsolutePath()
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val process = ProcessBuilder(javaExecutable.toString(), "-jar", jar.toString(), "--server.port=$port")
            .directory(Path.of("").toAbsolutePath().toFile())
            .redirectErrorStream(true)
            .redirectOutput(processLog.toFile())
            .apply {
                environment().putAll(mapOf(
                    "QUESTIONS_DATABASE_URL" to "${postgres.jdbcUrl}&currentSchema=questions",
                    "QUESTIONS_DATABASE_USER" to "questions",
                    "QUESTIONS_DATABASE_PASSWORD" to "questions-test",
                    "CORE_QUESTIONS_SERVICE_TOKEN" to "fixture-questions-core-token-1234567890",
                    "SPRING_FLYWAY_BASELINE_ON_MIGRATE" to "true",
                ))
                coreInternalSessionUrl?.let { environment()["CORE_INTERNAL_SESSION_URL"] = it }
            }
            .start()
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) throw AssertionError("Fresh Questions process exited before readiness with code ${process.exitValue()}: ${Files.readString(processLog)}")
            try {
                val health = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/health")).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString())
                if (health.statusCode() == 200) return QuestionsChild(process, port)
            } catch (_: Exception) {
                // The listener is not ready yet.
            }
            Thread.sleep(100)
        }
        stopQuestionsProcess(QuestionsChild(process, port))
        throw AssertionError("Fresh Questions process on port $port did not become healthy: ${Files.readString(processLog)}")
    }

    protected fun stopQuestionsProcess(child: QuestionsChild) {
        child.process.destroy()
        if (!child.process.waitFor(10, TimeUnit.SECONDS)) {
            child.process.destroyForcibly()
            assertTrue(child.process.waitFor(10, TimeUnit.SECONDS), "Questions child process did not terminate")
        }
    }

    protected fun startCoreProcess(questionSnapshotsUrl: String? = null): CoreChild {
        val jar = Path.of("build", "libs", "core-0.0.1-SNAPSHOT.jar").toAbsolutePath()
        assertTrue(Files.isRegularFile(jar), "Core boot jar missing at $jar")
        val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
        repeat(5) { attempt ->
            val port = ServerSocket(0).use { it.localPort }
            val processLog = Path.of("build", "core-process-$port-$attempt.log").toAbsolutePath()
            val process = ProcessBuilder(javaExecutable.toString(), "-jar", jar.toString(), "--server.port=$port")
                .directory(Path.of("").toAbsolutePath().toFile())
                .redirectErrorStream(true)
                .redirectOutput(processLog.toFile())
                .apply {
                    environment().putAll(mapOf(
                        "API_ALLOWED_ORIGINS" to "https://aja.example.test",
                        "CORE_DATABASE_URL" to "${postgres.jdbcUrl}&currentSchema=core",
                        "CORE_DATABASE_USER" to "core",
                        "CORE_DATABASE_PASSWORD" to "core-test",
                        "RABBITMQ_HOST" to rabbit.host,
                        "RABBITMQ_PORT" to rabbit.amqpPort.toString(),
                        "RABBITMQ_USER" to rabbit.adminUsername,
                        "RABBITMQ_PASSWORD" to rabbit.adminPassword,
                        "CORE_OUTBOX_ENCRYPTION_KEY" to java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }),
                        "CORE_OUTBOX_OPERATOR_TOKEN" to "fixture-outbox-operator-token-1234567890",
                        "CORE_QUESTIONS_SERVICE_TOKEN" to "fixture-questions-core-token-1234567890",
                    ))
                    questionSnapshotsUrl?.let { environment()["CORE_QUESTIONS_SNAPSHOTS_URL"] = it }
                }
                .start()
            val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
            var addressInUse = false
            while (System.nanoTime() < deadline) {
                if (!process.isAlive) {
                    val log = Files.readString(processLog)
                    if (log.contains("Port $port was already in use.")) {
                        addressInUse = true
                        break
                    }
                    throw AssertionError("Fresh Core process exited before readiness with code ${process.exitValue()}: $log")
                }
                try {
                    val health = client.send(HttpRequest.newBuilder(URI("http://localhost:$port/actuator/health")).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofString())
                    if (health.statusCode() == 200) return CoreChild(process, port)
                } catch (_: Exception) {
                    // The listener is not ready yet.
                }
                Thread.sleep(100)
            }
            if (addressInUse) return@repeat
            stopCoreProcess(CoreChild(process, port))
            throw AssertionError("Fresh Core process on port $port did not become healthy: ${Files.readString(processLog)}")
        }
        throw AssertionError("Could not start Core after 5 attempts because each selected port was already in use")
    }

    protected fun stopCoreProcess(child: CoreChild) {
        child.process.destroy()
        if (!child.process.waitFor(10, TimeUnit.SECONDS)) {
            child.process.destroyForcibly()
            assertTrue(child.process.waitFor(10, TimeUnit.SECONDS), "Core child process did not terminate")
        }
    }

    protected fun coreHttp(
        client: HttpClient,
        child: CoreChild,
        method: String,
        path: String,
        cookie: String,
        body: String? = null,
        key: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${child.port}/api/v1$path"))
            .timeout(Duration.ofSeconds(10))
            .header("Origin", "https://aja.example.test")
            .header("Cookie", "aja_session=$cookie")
            .header("Content-Type", "application/json")
        key?.let { builder.header("Idempotency-Key", it) }
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    protected fun createB06Class(adminCookie: String): String {
        val response = liveHttp(
            "POST", "/classes", adminCookie,
            """{"name":"B06 gate","code":"G-${UUID.randomUUID()}","year":2026,"shift":"Noite"}""",
            "class-${UUID.randomUUID()}",
        )
        assertEquals(201, response.statusCode(), response.body())
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).get("id").asText()
    }

    protected fun createB07WorkModule(adminCookie: String, teacherCookie: String, teacherId: String): Pair<String, String> {
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = liveHttp(
            "POST", "/modules", teacherCookie,
            """{"subjectId":"$subjectId","title":"B07 work fixture","description":""}""",
            "module-${UUID.randomUUID()}",
        )
        assertEquals(201, moduleResponse.statusCode(), moduleResponse.body())
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.body()).get("id").asText()
        return classId to moduleId
    }

    protected fun createB07Enrollment(classId: String, studentId: String, createdBy: String, unassignedIds: List<String> = emptyList(), enrolledAt: java.time.Instant? = mutableClock.instant()): String {
        val id = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        val email = jdbc.queryForObject("SELECT email FROM core.users WHERE id=?", String::class.java, studentId)!!
        val ids = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(unassignedIds)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by,enrolled_at,unassigned_assessment_ids)
            VALUES(?,1,?,?,?,?,?,?,?,'active',?,?,?::jsonb)""", id, now, now, createdBy, classId, email, email.lowercase(), studentId, createdBy, enrolledAt?.let(java.sql.Timestamp::from), ids)
        return id
    }

    protected fun createB07Assessment(moduleId: String, kind: String = "work", status: String = "published", allowLate: Boolean = false, deadline: java.time.Instant? = mutableClock.instant().plusSeconds(600)): String {
        val id = "assessment-${UUID.randomUUID()}"
        val publishedAt = if (status == "published") java.sql.Timestamp.from(mutableClock.instant().minusSeconds(1)) else null
        val authorId = jdbc.queryForObject("SELECT updated_by FROM core.modules WHERE id=?", String::class.java, moduleId)!!
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description)
            VALUES(?,?,?,?,1,?,?,?,?,?,?,?,?)""", id, moduleId, kind, status, publishedAt, deadline?.let(java.sql.Timestamp::from), allowLate, now, now, authorId, "B07 fixture", "")
        return id
    }

    protected fun createAttemptQuiz(moduleId: String, deadline: Instant, allowLate: Boolean): String {
        val assessmentId = createB07Assessment(moduleId, kind = "quiz", allowLate = allowLate, deadline = deadline)
        val questionId = "attempt-question-${UUID.randomUUID()}"
        val snapshot = """{"id":"$questionId","origin":"author","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}"""
        jdbc.update("""INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot)
            VALUES(?,?,0,'author',1,NULL,?::jsonb)""", assessmentId, questionId, snapshot)
        return assessmentId
    }

    protected fun createB06Subject(adminCookie: String, classId: String, teacherId: String): String {
        val response = liveHttp(
            "POST", "/subjects", adminCookie,
            """{"classId":"$classId","name":"Gate subject","teacherIds":["$teacherId"],"color":"blue","icon":"book","description":""}""",
            "subject-${UUID.randomUUID()}",
        )
        assertEquals(201, response.statusCode(), response.body())
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).get("id").asText()
    }

    protected fun awaitLockWait(queryFragment: String) {
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

    protected fun awaitLockWaitCount(expected: Int) {
        repeat(100) {
            val waiting = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.prepareStatement("select count(*) from pg_stat_activity where wait_event_type='Lock' and query not ilike '%pg_stat_activity%'").use { statement ->
                    statement.executeQuery().use { result -> result.next(); result.getInt(1) }
                }
            }
            if (waiting >= expected) return
            Thread.sleep(25)
        }
        assertTrue(false, "Expected $expected simultaneous PostgreSQL lock waiters")
    }

    protected fun latestOutboxPayload(eventType: String): String {
        val eventId = jdbc.queryForObject("select id from core.outbox_events where event_type = ? order by created_at desc limit 1", String::class.java, eventType)!!
        return outboxPayload(eventId, eventType)
    }

    protected fun outboxPayload(eventId: String, eventType: String): String {
        val event = jdbc.queryForMap("select nonce, encrypted_payload from core.outbox_events where id=? AND event_type=?", eventId, eventType)
        val nonce = event["nonce"] as ByteArray
        val encryptedPayload = event["encrypted_payload"] as ByteArray
        val decrypt = Cipher.getInstance("AES/GCM/NoPadding")
        decrypt.init(Cipher.DECRYPT_MODE, SecretKeySpec(ByteArray(32) { 7 }, "AES"), GCMParameterSpec(128, nonce))
        decrypt.updateAAD("$eventId|$eventType|1".toByteArray())
        return String(decrypt.doFinal(encryptedPayload))
    }

    protected fun awaitPublished(eventId: String) {
        repeat(100) {
            if (jdbc.queryForObject("select published_at is not null from core.outbox_events where id=?", Boolean::class.java, eventId) == true) return
            Thread.sleep(50)
        }
        assertTrue(false, "Outbox event was not confirmed by RabbitMQ")
    }

    protected fun declareEmailQueueTopology() {
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
            channel.queueBind("email.identity.events", "aja.events", "academic.announcement-notification-requested")
        }
    }

    protected fun receivePublishedEvent(eventId: String): String {
        repeat(40) {
            val message = rabbitTemplate.receive("email.identity.events", 250) ?: return@repeat
            val body = String(message.body)
            if (body.contains(eventId)) return body
        }
        assertTrue(false, "Published event was not present in the durable email queue")
        return ""
    }

    protected fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    protected fun adminSession() = userSession("admin")

    protected fun userSession(role: String): Pair<String, String> {
        val id = UUID.randomUUID().toString()
        val cookie = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(java.security.SecureRandom()::nextBytes))
        val now = java.sql.Timestamp.from(mutableClock.instant())
        val email = "admin-$id@example.test"
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?)""", id, now, now, id, "Admin AJA", email, email, "not-a-password-hash", role, true, now)
        jdbc.update("INSERT INTO core.sessions(id,user_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,?)", UUID.randomUUID().toString(), id, sha256(cookie), now, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(3600)))
        return cookie to id
    }

    companion object {
        private val databaseInitialised = java.util.concurrent.atomic.AtomicBoolean(false)
        private val pendingCoverageClasses = java.util.concurrent.atomic.AtomicInteger(-1)

        @JvmField val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
        @JvmField val rabbit = RabbitMQContainer("rabbitmq:4-management-alpine")

        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            registry.add("API_ALLOWED_ORIGINS") { "https://aja.example.test" }
            registry.add("core.outbox.encryption-key") { java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 }) }
            registry.add("core.outbox.operator-token") { "fixture-outbox-operator-token-1234567890" }
            registry.add("core.internal.questions-token") { "fixture-questions-core-token-1234567890" }
            postgres.start()
            rabbit.start()
            if (databaseInitialised.compareAndSet(false, true)) {
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
            }
            registry.add("spring.datasource.url") { "${postgres.jdbcUrl}&currentSchema=core" }
            registry.add("spring.datasource.username") { "core" }
            registry.add("spring.datasource.password") { "core-test" }
            registry.add("spring.rabbitmq.host") { rabbit.host }
            registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
            registry.add("spring.rabbitmq.username") { rabbit.adminUsername }
            registry.add("spring.rabbitmq.password") { rabbit.adminPassword }
        }

        /**
         * Number of concrete per-feature classes that inherit this base. The aggregate OpenAPI
         * assertion runs once, after the last of them, instead of once per class.
         */
        private fun featureClassCount(): Int {
            val base = CoreIntegrationTestBase::class.java
            val location = base.protectionDomain.codeSource.location.toURI()
            val directory = java.nio.file.Paths.get(location).resolve(base.packageName.replace('.', '/'))
            if (!java.nio.file.Files.isDirectory(directory)) return 1
            return java.nio.file.Files.list(directory).use { files ->
                files.map { it.fileName.toString() }
                    .filter { it.endsWith("IntegrationTests.class") && !it.contains('$') }
                    .map { Class.forName("${base.packageName}.${it.removeSuffix(".class")}", false, base.classLoader) }
                    .filter { base.isAssignableFrom(it) && !java.lang.reflect.Modifier.isAbstract(it.modifiers) }
                    .count()
                    .toInt()
            }.coerceAtLeast(1)
        }
    }
}
