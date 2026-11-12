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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.mock.web.MockMultipartFile
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
import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.ManageAcademics

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
    private val manageAcademics: ManageAcademics,
) {
    @Test
    fun stagedMaterialFileIsStoredAndReadableOnlyByItsAuthorizedOwner() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (outsiderCookie, _) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"File module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val bytes = "%PDF-1.4\nAJA fixture\n%%EOF".toByteArray()
        val upload = MockMultipartFile("file", "lesson.pdf", "application/pdf", bytes)
        mvc.perform(multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId).header("Origin", "https://aja.example.test").header("Idempotency-Key", "unauth-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized)
        val invalidPdf = MockMultipartFile("file", "lesson.pdf", "application/pdf", "not a PDF".toByteArray())
        mvc.perform(multipart("/api/v1/files").file(invalidPdf).param("purpose", "material").param("parentId", moduleId).cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", "invalid-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity)
        val uploadKey = "upload-${UUID.randomUUID()}"
        val uploaded = mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test")
                .header("Idempotency-Key", uploadKey),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(uploaded.response.contentAsString).get("id").asText()

        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("staged") }
            jsonPath("$.size") { value(bytes.size) }
            jsonPath("$.path") { doesNotExist() }
            jsonPath("$.bytes") { doesNotExist() }
        }
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect {
            status { isOk() }
            header { string("Content-Type", "application/pdf") }
            header { string("X-Content-Type-Options", "nosniff") }
            content { bytes(bytes) }
        }
        assertEquals(bytes.size, jdbc.queryForObject("SELECT size FROM core.files WHERE id=?", Long::class.java, fileId)?.toInt())
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, jdbc.queryForObject("SELECT sha256 FROM core.files WHERE id=?", String::class.java, fileId))
        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isNotFound() } }
        mvc.post("/api/v1/files/$fileId/discard") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = "{}"
        }.andExpect { status { isNoContent() } }
        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", uploadKey),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun workPreparationIsOwnedByStudentAndScopedToItsContext() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val key = "work-${UUID.randomUUID()}"
        val bytes = "arbitrary work attachment".toByteArray()
        val upload = MockMultipartFile("file", "draft.bin", "application/octet-stream", bytes)
        val result = mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "work").param("parentId", assessmentId)
                .cookie(Cookie("aja_session", studentCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", key),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("id").asText()

        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.purpose") { value("work") }; jsonPath("$.status") { value("staged") } }
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; content { bytes(bytes) } }
        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isNotFound() } }

        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "work").param("parentId", assessmentId)
                .cookie(Cookie("aja_session", studentCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", key),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)
        assertEquals(studentId, jdbc.queryForObject("SELECT owner_id FROM core.files WHERE id=?", String::class.java, fileId))

        jdbc.update("UPDATE core.enrollments SET status='ended',ended_at=? WHERE student_id=?", java.sql.Timestamp.from(mutableClock.instant()), studentId)
        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isNotFound() } }
        mvc.post("/api/v1/files/$fileId/discard") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = "{}"
        }.andExpect { status { isNotFound() } }

        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "work").param("parentId", assessmentId)
                .cookie(Cookie("aja_session", studentCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", key),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)

        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "work").param("parentId", assessmentId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", "teacher-${UUID.randomUUID()}"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun workPreparationRequiresPublishedWorkAndCurrentAssignmentBeforeDeadlineRules() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (unassignedCookie, unassignedStudentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val deadline = mutableClock.instant().plusSeconds(5)
        val draftId = createB07Assessment(moduleId, status = "draft")
        val quizId = createB07Assessment(moduleId, kind = "quiz")
        val unassignedId = createB07Assessment(moduleId)
        val lateId = createB07Assessment(moduleId, allowLate = false, deadline = deadline)
        val allowedLateId = createB07Assessment(moduleId, allowLate = true, deadline = deadline)
        val entryAfterExpiryId = createB07Assessment(moduleId, allowLate = true, deadline = deadline)
        createB07Enrollment(classId, studentId, teacherId)
        createB07Enrollment(classId, unassignedStudentId, teacherId, unassignedIds = listOf(unassignedId), enrolledAt = deadline.plusSeconds(10))

        fun uploadStatus(cookie: String, assessmentId: String, suffix: String) = mvc.perform(
            multipart("/api/v1/files").file(MockMultipartFile("file", "draft.bin", "application/octet-stream", byteArrayOf(1)))
                .param("purpose", "work").param("parentId", assessmentId).cookie(Cookie("aja_session", cookie))
                .header("Origin", "https://aja.example.test").header("Idempotency-Key", "$suffix-${UUID.randomUUID()}"),
        ).andReturn().response.status

        assertEquals(404, uploadStatus(studentCookie, "missing-assessment-${UUID.randomUUID()}", "missing-work"))
        assertEquals(404, uploadStatus(studentCookie, draftId, "draft-work"))
        assertEquals(404, uploadStatus(studentCookie, quizId, "quiz-work"))
        assertEquals(404, uploadStatus(unassignedCookie, unassignedId, "unassigned-work"))

        mutableClock.advance(Duration.ofSeconds(5))
        assertEquals(404, uploadStatus(studentCookie, lateId, "late-work"))
        assertEquals(201, uploadStatus(studentCookie, allowedLateId, "allowed-late-work"))
        assertEquals(404, uploadStatus(unassignedCookie, entryAfterExpiryId, "entry-after-expiry-work"))
    }

    @Test
    fun missingModuleRejectsMaterialPreparationWithoutCreatingAFile() {
        val (teacherCookie, _) = userSession("teacher")
        val bytes = "%PDF-1.4\\nmissing parent\\n%%EOF".toByteArray()
        val upload = MockMultipartFile("file", "missing.pdf", "application/pdf", bytes)
        val before = jdbc.queryForObject("SELECT count(*) FROM core.files", Int::class.java)

        mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", "missing-module-${UUID.randomUUID()}")
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test").header("Idempotency-Key", "missing-${UUID.randomUUID()}"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound)

        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM core.files", Int::class.java))
    }

    @Test
    fun failedMaterialIdempotencySaveRollsBackPublicationAndAllowsRetry() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Atomic batch","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"Lesson","kind":"lesson","duration":10,"content":"Body"}]}"""
        val key = "atomic-${UUID.randomUUID()}"
        jdbc.execute("""CREATE OR REPLACE FUNCTION core.fail_b07_idempotency() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'} BEGIN RAISE EXCEPTION 'forced idempotency failure'; END; ${'$'}${'$'}""")
        jdbc.execute("CREATE TRIGGER fail_b07_idempotency BEFORE INSERT OR UPDATE ON core.command_idempotency FOR EACH ROW EXECUTE FUNCTION core.fail_b07_idempotency()")
        try {
            mvc.post("/api/v1/materials/batch") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", key)
                cookie(Cookie("aja_session", teacherCookie))
                content = command
            }.andExpect { status { isInternalServerError() } }
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_b07_idempotency ON core.command_idempotency")
            jdbc.execute("DROP FUNCTION IF EXISTS core.fail_b07_idempotency()")
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE module_id=?", Int::class.java, moduleId))
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM core.modules WHERE id=?", Int::class.java, moduleId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.command_idempotency WHERE actor_id=? AND operation='publishMaterials' AND target='/materials/batch' AND idempotency_key=?", Int::class.java, teacherId, key))

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.total") { value(1) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE module_id=?", Int::class.java, moduleId))
    }

    @Test
    fun materialBatchReplayRechecksCurrentTeacherAssignment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Replay scope","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"Lesson","kind":"lesson","duration":10,"content":"Body"}]}"""
        val key = "replay-${UUID.randomUUID()}"
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() } }
        jdbc.update("DELETE FROM core.subject_teachers WHERE subject_id=? AND teacher_id=?", subjectId, teacherId)

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun materialBatchIsAtomicAndProgressConclusionIsRepeatable() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Material module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val upload = MockMultipartFile("file", "lesson.pdf", "application/pdf", "%PDF-1.4\nmaterial\n%%EOF".toByteArray())
        val uploadResult = mvc.perform(
            multipart("/api/v1/files").file(upload).param("purpose", "material").param("parentId", moduleId)
                .cookie(Cookie("aja_session", teacherCookie)).header("Origin", "https://aja.example.test")
                .header("Idempotency-Key", "upload-${UUID.randomUUID()}"),
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(uploadResult.response.contentAsString).get("id").asText()
        val command = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"PDF lesson","kind":"pdf","duration":10,"content":"","fileId":"$fileId"}]}"""

        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "batch-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = command.replace("\"fileId\":\"$fileId\"", "\"fileId\":\"missing-file\"")
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/materials?moduleId=$moduleId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/files/$fileId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("staged") } }

        val batchKey = "batch-${UUID.randomUUID()}"
        val published = mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", batchKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.total") { value(1) } }.andReturn()
        val materialId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(published.response.contentAsString).get("items").get(0).get("id").asText()
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", batchKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = command
        }.andExpect { status { isCreated() }; jsonPath("$.items[0].id") { value(materialId) } }
        mvc.get("/api/v1/materials/$materialId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect {
            status { isOk() }
            jsonPath("$.material.fileId") { value(fileId) }
            jsonPath("$.material.file.path") { doesNotExist() }
            jsonPath("$.module.id") { value(moduleId) }
            jsonPath("$.subject.id") { value(subjectId) }
        }
        mvc.patch("/api/v1/me/progress") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"materialId":"$materialId","completed":true}"""
        }.andExpect { status { isOk() }; jsonPath("$.completed") { value(true) } }
        mutableClock.advance(Duration.ofMinutes(1))
        mvc.patch("/api/v1/me/progress") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"materialId":"$materialId"}"""
        }.andExpect { status { isOk() }; jsonPath("$.completed") { value(true) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.material_progress WHERE user_id=? AND material_id=? AND completed", Int::class.java, teacherId, materialId))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.materials WHERE id=?", Int::class.java, materialId))
    }

    @Test
    fun materialReorderRequiresTheCompleteCurrentRevisionSet() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResult = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Order module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResult.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/materials/batch") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "batch-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","expectedRevision":1,"items":[{"moduleId":"$moduleId","title":"First","kind":"lesson","duration":1,"content":"first"},{"moduleId":"$moduleId","title":"Second","kind":"lesson","duration":1,"content":"second"}]}"""
        }.andExpect { status { isCreated() } }
        val page = mvc.get("/api/v1/materials?moduleId=$moduleId&sort=order") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(2) } }.andReturn()
        val items = tools.jackson.databind.json.JsonMapper.builder().build().readTree(page.response.contentAsString).get("items")
        val firstId = items.get(0).get("id").asText()
        val secondId = items.get(1).get("id").asText()
        val reorder = """{"expectedRevision":2,"items":[{"id":"$secondId","expectedRevision":1},{"id":"$firstId","expectedRevision":1}]}"""
        mvc.post("/api/v1/modules/$moduleId/reorder-materials") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = reorder
        }.andExpect { status { isOk() }; jsonPath("$.items[0].id") { value(secondId) } }
        mvc.post("/api/v1/modules/$moduleId/reorder-materials") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = reorder
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT order_index FROM core.materials WHERE id=?", Int::class.java, secondId))
        assertEquals(1, jdbc.queryForObject("SELECT order_index FROM core.materials WHERE id=?", Int::class.java, firstId))
    }

    @Test
    fun questionImageImportIsPrivateAndCurrentImageBytesAreReadableByAnActiveSession() {
        val (studentCookie, _) = userSession("student")
        val imageBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + "test-image".toByteArray()
        val image = MockMultipartFile("file", "question.png", "image/png", imageBytes)
        mvc.perform(multipart("/internal/v1/question-files/question-image-1").file(image).param("questionId", "q-image-1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden)
        mvc.perform(multipart("/internal/v1/question-files/question-image-1").file(image).param("questionId", "q-image-1").header("X-AJA-Service-Token", "fixture-questions-core-token-1234567890"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated)
        mvc.get("/api/v1/files/question-image-1/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect {
            status { isOk() }
            header { string("Content-Type", "image/png") }
            header { string("X-Content-Type-Options", "nosniff") }
            content { bytes(imageBytes) }
        }
    }

    @Test
    fun academicOperationsRequireAnActiveSession() {
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Hidden","code":"HIDDEN","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun assignedTeachersCanCollaborateOnSubjectsAndUnassignmentRevokesWrites() {
        val (adminCookie, _) = adminSession()
        val (teacherOneCookie, teacherOneId) = userSession("teacher")
        val (teacherTwoCookie, teacherTwoId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (otherStudentCookie, otherStudentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classResponse = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma B06","code":"B06-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResponse.response.contentAsString).get("id").asText()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        listOf(studentId, otherStudentId).forEach { id ->
            val email = jdbc.queryForObject("select email from core.users where id=?", String::class.java, id)!!
            jdbc.update("INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by) VALUES(?,1,?,?,?,?,?,?,?,'active',?)", UUID.randomUUID().toString(), now, now, teacherOneId, classId, email, email.lowercase(), id, teacherOneId)
        }

        val subjectKey = "subject-${UUID.randomUUID()}"
        val subjectResponse = mvc.post("/api/v1/subjects") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", subjectKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","name":"Matemática","teacherIds":["$teacherOneId","$teacherTwoId"],"color":"violet","description":""}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.teacherIds.length()") { value(2) }
            jsonPath("$.className") { doesNotExist() }
            jsonPath("$.teachers") { doesNotExist() }
            jsonPath("$.canManageContent") { doesNotExist() }
            jsonPath("$.publicationIds") { doesNotExist() }
            jsonPath("$.hasUpdates") { doesNotExist() }
        }.andReturn()
        val subjectId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(subjectResponse.response.contentAsString).get("id").asText()

        val firstModuleKey = "module-${UUID.randomUUID()}"
        val firstModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", firstModuleKey)
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo A","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val secondModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo B","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val adminModule = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo C","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val firstModuleId = json.readTree(firstModule.response.contentAsString).get("id").asText()
        val secondModuleId = json.readTree(secondModule.response.contentAsString).get("id").asText()
        val adminModuleId = json.readTree(adminModule.response.contentAsString).get("id").asText()

        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":3,"items":[{"id":"$secondModuleId","expectedRevision":1},{"id":"$firstModuleId","expectedRevision":1},{"id":"$adminModuleId","expectedRevision":1}]}"""
        }.andExpect { status { isConflict() } }
        mvc.get("/api/v1/subjects/$subjectId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.modules.length()") { value(3) }; jsonPath("$.modules[0].id") { value(firstModuleId) } }
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            param("page", "2")
            param("pageSize", "1")
            content = """{"expectedRevision":4,"items":[{"id":"$secondModuleId","expectedRevision":1},{"id":"$firstModuleId","expectedRevision":1},{"id":"$adminModuleId","expectedRevision":1}]}"""
        }.andExpect { status { isOk() }; jsonPath("$.page") { value(2) }; jsonPath("$.pageSize") { value(1) }; jsonPath("$.total") { value(3) }; jsonPath("$.items.length()") { value(1) }; jsonPath("$.items[0].id") { value(firstModuleId) } }
        mvc.get("/api/v1/modules?subjectId=$subjectId&page=1&pageSize=1&sort=order") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.pageSize") { value(1) }; jsonPath("$.total") { value(3) }; jsonPath("$.items.length()") { value(1) } }
        mvc.get("/api/v1/modules/$firstModuleId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.title") { value("Módulo A") } }
        mvc.patch("/api/v1/modules/$firstModuleId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":2,"title":"Módulo A editado"}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(3) }; jsonPath("$.description") { value("") } }

        mvc.patch("/api/v1/subjects/$subjectId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":5,"teacherIds":["$teacherTwoId"]}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(6) }; jsonPath("$.className") { doesNotExist() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Revoked","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", firstModuleKey)
            cookie(Cookie("aja_session", teacherOneCookie))
            content = """{"subjectId":"$subjectId","title":"Módulo A","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/subjects/$subjectId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.modules.length()") { value(3) }; jsonPath("$.projections.materials.count") { value(0) }; jsonPath("$.subject.teachers.length()") { value(1) }; jsonPath("$.subject.canManageContent") { value(true) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(subjectId) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) }; jsonPath("$.items.length()") { value(0) } }
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "material:observed")
        val firstVisit = mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.seenAt") { value(null) }; jsonPath("$.publicationIds.length()") { value(1) }; jsonPath("$.hasUpdates") { value(true) } }.andReturn()
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "notice:later")
        mvc.post("/api/v1/subjects/$subjectId/visits") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"publicationIds":["material:observed"]}"""
        }.andExpect { status { isOk() }; jsonPath("$.seenAt") { exists() }; jsonPath("$.seenPublicationIds[0]") { value("material:observed") }; jsonPath("$.publicationIds.length()") { value(2) }; jsonPath("$.hasUpdates") { value(true) } }
        val visitPool = Executors.newFixedThreadPool(2)
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "material:concurrent-a")
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "notice:concurrent-b")
        try {
            val concurrentVisits = listOf("material:concurrent-a", "notice:concurrent-b").map { publicationId ->
                visitPool.submit<Int> {
                    mvc.post("/api/v1/subjects/$subjectId/visits") {
                        contentType = org.springframework.http.MediaType.APPLICATION_JSON
                        header("Origin", "https://aja.example.test")
                        cookie(Cookie("aja_session", studentCookie))
                        content = json.writeValueAsString(mapOf("publicationIds" to listOf(publicationId)))
                    }.andReturn().response.status
                }
            }
            assertEquals(listOf(200, 200), concurrentVisits.map { it.get(10, TimeUnit.SECONDS) })
        } finally { visitPool.shutdownNow() }
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.seenPublicationIds.length()") { value(3) }; jsonPath("$.seenPublicationIds[0]") { value("material:concurrent-a") }; jsonPath("$.seenPublicationIds[2]") { value("notice:concurrent-b") } }
        mvc.post("/api/v1/subjects/$subjectId/visits") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"publicationIds":["material:unknown"]}"""
        }.andExpect { status { isUnprocessableEntity() } }
        val manyPublications = (1..100).map { "material:batch-$it" } + ("material:" + "x".repeat(130))
        manyPublications.forEach { jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, it) }
        val manyVisit = mvc.post("/api/v1/subjects/$subjectId/visits") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = json.writeValueAsString(mapOf("publicationIds" to manyPublications))
        }.andExpect { status { isOk() }; jsonPath("$.seenPublicationIds.length()") { value(104) } }.andReturn()
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", otherStudentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.seenAt") { value(null) }; jsonPath("$.seenPublicationIds.length()") { value(0) }; jsonPath("$.hasUpdates") { value(true) } }
        assertTrue(firstVisit.response.contentAsString.contains("\"seenAt\":null"))

        mvc.post("/api/v1/classes/$classId/close") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"subjectId":"$subjectId","title":"Closed teacher write","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"subjectId":"$subjectId","title":"Closed admin write","description":""}"""
        }.andExpect { status { isForbidden() } }
        mvc.patch("/api/v1/modules/$firstModuleId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":3,"title":"Closed teacher edit"}"""
        }.andExpect { status { isForbidden() } }
        mvc.patch("/api/v1/modules/$secondModuleId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":2,"title":"Closed admin edit"}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
            content = """{"expectedRevision":6,"items":[{"id":"$secondModuleId","expectedRevision":2},{"id":"$firstModuleId","expectedRevision":3},{"id":"$adminModuleId","expectedRevision":2}]}"""
        }.andExpect { status { isForbidden() } }
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":6,"items":[{"id":"$secondModuleId","expectedRevision":2},{"id":"$firstModuleId","expectedRevision":3},{"id":"$adminModuleId","expectedRevision":2}]}"""
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/subjects/$subjectId/visits") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() } }
        mvc.get("/api/v1/subjects/$subjectId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.subject.canManageContent") { value(false) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherTwoCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].canManageContent") { value(false) } }
        mvc.get("/api/v1/subjects?classId=$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].canManageContent") { value(false) } }
    }

    @Test
    fun concurrentModuleCreateAndReorderPreserveOrderAndReturnBoundedPage() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma concorrente","code":"RACE-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val classId = json.readTree(classResult.response.contentAsString).get("id").asText()
        val subjectResult = mvc.post("/api/v1/subjects") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "subject-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","name":"Concorrência","teacherIds":["$teacherId"],"color":"blue","icon":"book","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val subjectId = json.readTree(subjectResult.response.contentAsString).get("id").asText()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        repeat(101) { index ->
            jdbc.update("INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES(?,1,?,?,?,?,?,?,?)", "module-${index.toString().padStart(3, '0')}", now, now, teacherId, subjectId, "Module $index", "", index)
        }
        val itemsBefore = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? ORDER BY order_index", { rs, _ -> mapOf("id" to rs.getString(1), "expectedRevision" to rs.getInt(2)) }, subjectId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.prepareStatement("SELECT id FROM core.subjects WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, subjectId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Racing module","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWait("core.subjects")
            val reorder = pool.submit<Int> {
                mvc.post("/api/v1/subjects/$subjectId/reorder-modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", adminCookie))
                    content = json.writeValueAsString(mapOf("expectedRevision" to 1, "items" to itemsBefore))
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(201, create.get(10, TimeUnit.SECONDS))
            assertEquals(409, reorder.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(102, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId))
        val allItems = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index,id", { rs, _ -> mapOf("id" to rs.getString(1), "expectedRevision" to rs.getInt(2)) }, subjectId)
        mvc.post("/api/v1/subjects/$subjectId/reorder-modules?page=2&pageSize=100") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = json.writeValueAsString(mapOf("expectedRevision" to 2, "items" to allItems))
        }.andExpect { status { isOk() }; jsonPath("$.page") { value(2) }; jsonPath("$.pageSize") { value(100) }; jsonPath("$.total") { value(102) }; jsonPath("$.items.length()") { value(2) } }
        val orderedIds = jdbc.query("SELECT id FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index", { rs, _ -> rs.getString(1) }, subjectId)
        assertEquals(102, orderedIds.distinct().size)
        assertTrue(orderedIds.contains("module-100"))

        val heldForTeamUpdate = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        heldForTeamUpdate.autoCommit = false
        heldForTeamUpdate.prepareStatement("SELECT id FROM core.subjects WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, subjectId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val teamPool = Executors.newFixedThreadPool(2)
        try {
            val teacherWrite = teamPool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Concurrent teacher write","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWait("core.subjects")
            val teamUpdate = teamPool.submit<Int> {
                mvc.patch("/api/v1/subjects/$subjectId") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":3,"teacherIds":["$teacherId"]}"""
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            heldForTeamUpdate.commit()
            assertEquals(201, teacherWrite.get(10, TimeUnit.SECONDS))
            assertEquals(409, teamUpdate.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { heldForTeamUpdate.rollback() }
            heldForTeamUpdate.close()
            teamPool.shutdownNow()
        }
        assertEquals(103, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId))
    }

    @Test
    fun sessionRevocationQueuedBeforeModuleWriteWinsSharedAuthGate() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(812734)") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") {
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", teacherCookie))
                }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Revoked while queued","description":""}"""
                }.andReturn().response.status
            }
            repeat(80) { if (!create.isDone) Thread.sleep(25) }
            assertFalse(create.isDone, "Module write must wait for the auth invariant gate held by the earlier revocation")
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
            assertEquals(403, create.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND title='Revoked while queued'", Int::class.java, subjectId))
    }

    @Test
    fun moduleWriteThatOwnsAuthGateCommitsBeforeQueuedLogout() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.prepareStatement("SELECT id FROM core.classes WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, classId)
            statement.executeQuery().use { assertTrue(it.next()) }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val create = pool.submit<Int> {
                mvc.post("/api/v1/modules") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "module-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"subjectId":"$subjectId","title":"Write before logout","description":""}"""
                }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") {
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", teacherCookie))
                }.andReturn().response.status
            }
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(201, create.get(10, TimeUnit.SECONDS))
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.modules WHERE subject_id=? AND title='Write before logout'", Int::class.java, subjectId))
        mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"After logout","description":""}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun teacherProvisioningAndEmailConfirmationActivatePendingEnrollmentAtomically() {
        val adminCookie = adminSession().first
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Turma teste","code":"C-${UUID.randomUUID()}","year":2026,"shift":"Manhã"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        mvc.get("/api/v1/enrollments/preview?classId=$classId&email=not-an-email") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isUnprocessableEntity() } }

        val teacherEmail = "teacher-${UUID.randomUUID()}@example.test"
        val outboxBefore = jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java)!!
        mvc.post("/api/v1/users") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "user-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Teacher AJA","email":"$teacherEmail","role":"teacher"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.emailConfirmedAt") { value(null) } }
        assertEquals(outboxBefore + 2, jdbc.queryForObject("select count(*) from core.outbox_events", Int::class.java))
        assertEquals(2, jdbc.queryForObject("select count(*) from core.auth_tokens where email_at_issue=?", Int::class.java, teacherEmail))

        val studentEmail = "student-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Student AJA","email":"$studentEmail","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollmentKey = "enrollment-${UUID.randomUUID()}"
        val enrollment = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$studentEmail"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("pending") } }.andReturn()
        val enrollmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(enrollment.response.contentAsString).get("id").asText()
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val confirmation = mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$token"}"""
        }.andReturn()
        assertEquals(200, confirmation.response.status, confirmation.response.contentAsString)
        val activated = jdbc.queryForMap("select status,student_id from core.enrollments where id=?", enrollmentId)
        assertEquals("active", activated["status"])
        val studentId = jdbc.queryForObject("select id from core.users where normalized_email=?", String::class.java, studentEmail)!!
        assertEquals(studentId, activated["student_id"])
        mvc.get("/api/v1/enrollments?classId=$classId&status=active&q=$studentEmail&page=1&pageSize=1&sort=-id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        mvc.get("/api/v1/students?classId=$classId&history=false") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].email") { value(studentEmail) }; jsonPath("$.items[0].enrollment.id") { value(enrollmentId) } }
        mvc.get("/api/v1/students?q=$studentEmail") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].enrollment") { value(null) } }
        mvc.get("/api/v1/students/$studentId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.enrollments[0].id") { value(enrollmentId) } }

        val login = mvc.post("/api/v1/auth/login") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"email":"$studentEmail","password":"A long test password"}"""
        }.andExpect { status { isOk() } }.andReturn()
        val studentCookie = login.response.getHeader("Set-Cookie")!!.substringAfter("aja_session=").substringBefore(';')
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(classId) }; jsonPath("$.items[0].studentIds") { doesNotExist() } }
        mvc.get("/api/v1/classes/$classId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.id") { value(classId) } }
        mvc.get("/api/v1/students?classId=$classId&q=$studentEmail&history=true") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/students/$studentId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isForbidden() } }
        mvc.get("/api/v1/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        mvc.get("/api/v1/me/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].id") { value(enrollmentId) } }
        val repeated = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$studentEmail"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        assertEquals(enrollmentId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(repeated.response.contentAsString).get("id").asText())
        mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", enrollmentKey)
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"other-${UUID.randomUUID()}@example.test"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("IDEMPOTENCY_CONFLICT") } }
        mvc.post("/api/v1/classes/$classId/close") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        assertEquals("ended", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
        mvc.post("/api/v1/classes/$classId/reopen") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "reopen-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":2}"""
        }.andExpect { status { isOk() } }
        assertEquals("ended", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
    }

    @Test
    fun concurrentAdminDeactivationCannotRemoveTheLastAdministrator() {
        jdbc.update("UPDATE core.sessions SET revoked_at=now() WHERE user_id IN (SELECT id FROM core.users WHERE role='admin') AND revoked_at IS NULL")
        jdbc.update("UPDATE core.users SET active=false WHERE role='admin'")
        val first = adminSession(); val second = adminSession()
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requests = listOf(first, second).map { (cookie, id) -> executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/users/$id/deactivate") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    cookie(Cookie("aja_session", cookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            } }
            start.countDown()
            val statuses = requests.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(200, 409), statuses.sorted())
            assertEquals(1, jdbc.queryForObject("select count(*) from core.users where role='admin' and active", Int::class.java))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun roleChangesRevokeSessionsAndStaleRevisionsConflict() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/students") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/students/${UUID.randomUUID()}") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/enrollments") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(0) } }
        mvc.get("/api/v1/users?role=teacher&active=true&q=Admin%20AJA") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items.length()") { value(0) } }
        mvc.post("/api/v1/users/$teacherId/role") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1,"role":"student"}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(2) } }
        mvc.get("/api/v1/classes") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/api/v1/users/$teacherId/role") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1,"role":"teacher"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.details.currentRevision") { value(2) } }
    }

    @Test
    fun emailConfirmationRollsBackWhenPendingEnrollmentsConflict() {
        val adminCookie = adminSession().first
        val classes = (1..2).map { number ->
            val result = mvc.post("/api/v1/classes") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", "class-${UUID.randomUUID()}")
                cookie(Cookie("aja_session", adminCookie))
                content = """{"name":"Turma $number","code":"R-${UUID.randomUUID()}","year":2026,"shift":"Tarde"}"""
            }.andReturn()
            tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("id").asText()
        }
        val email = "atomic-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Atomic Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollments = classes.map { classId ->
            mvc.post("/api/v1/enrollments") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
                cookie(Cookie("aja_session", adminCookie))
                content = """{"classId":"$classId","email":"$email"}"""
            }.andExpect { status { isCreated() } }.andReturn()
        }.map { tools.jackson.databind.json.JsonMapper.builder().build().readTree(it.response.contentAsString).get("id").asText() }
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val failed = mvc.post("/api/v1/auth/email-confirmations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"token":"$token"}"""
        }.andReturn()
        assertEquals(409, failed.response.status)
        assertEquals(0, jdbc.queryForObject("select count(*) from core.users where normalized_email=? and email_confirmed_at is not null", Int::class.java, email))
        assertEquals(2, jdbc.queryForObject("select count(*) from core.enrollments where id in (?,?) and status='pending'", Int::class.java, enrollments[0], enrollments[1]))
        assertEquals(0, jdbc.queryForObject("select count(*) from core.auth_tokens where token_hash=? and used_at is not null", Int::class.java, sha256(token)))
    }

    @Test
    fun administrativeActivationLinksOnlyAnAlreadyConfirmedAccount() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Activation class","code":"A-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val studentId = UUID.randomUUID().toString()
        val enrollmentId = UUID.randomUUID().toString()
        val email = "Confirmed-${UUID.randomUUID()}@Example.Test"
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at)
            VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", studentId, now, now, studentId, "Confirmed Student", email, email.lowercase(), "unused", now)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by)
            VALUES(?,1,?,?,?,?,?,?,NULL,'pending',?)""", enrollmentId, now, now, adminId, classId, email, email.lowercase(), adminId)
        mvc.post("/api/v1/enrollments/$enrollmentId/activate") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "activate-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() }; jsonPath("$.studentId") { value(studentId) }; jsonPath("$.status") { value("active") } }
        assertEquals(1, jdbc.queryForObject("select revision from core.users where id=?", Int::class.java, studentId))
        assertTrue(jdbc.queryForObject("select active and email_confirmed_at is not null from core.users where id=?", Boolean::class.java, studentId)!!)
        mvc.post("/api/v1/enrollments/$enrollmentId/end") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "end-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":2}"""
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("ended") } }
        val renewed = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$email"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.status") { value("active") } }.andReturn()
        val renewedId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(renewed.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/enrollments/$renewedId/cancel") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "cancel-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("cancelled") } }
    }

    @Test
    fun academicMutationsWriteMinimalAuditAndIdempotentReplayDoesNotDuplicateIt() {
        val (adminCookie, adminId) = adminSession()
        val before = jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java)!!
        val key = "class-audit-${UUID.randomUUID()}"
        val code = "AU-${UUID.randomUUID()}"
        val body = """{"name":"Audited class","code":"$code","year":2026,"shift":"Tarde"}"""
        val first = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", adminCookie))
            content = body
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(first.response.contentAsString).get("id").asText()
        assertEquals(before + 1, jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java))
        assertEquals("academic.class_created", jdbc.queryForObject("select event_type from core.audit_events where subject_user_id=?", String::class.java, classId))
        assertEquals(adminId, jdbc.queryForObject("select actor_user_id from core.audit_events where subject_user_id=?", String::class.java, classId))
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", adminCookie))
            content = body
        }.andExpect { status { isCreated() } }
        assertEquals(before + 1, jdbc.queryForObject("select count(*) from core.audit_events", Int::class.java))
        mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "different-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Duplicate class","code":"$code","year":2026,"shift":"Tarde"}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("BUSINESS_CONFLICT") } }
    }

    @Test
    fun adminWriteRevalidatesCurrentSessionAndRoleInsideTheCommandBoundary() {
        val (cookie, adminId) = adminSession()
        val sessionHash = sha256(cookie)
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sessionHash)
        val actor = AcademicActor(adminId, "admin", true, mutableClock.instant(), sessionHash)
        val before = jdbc.queryForObject("select count(*) from core.classes", Int::class.java)
        assertThrows(AcademicForbidden::class.java) {
            manageAcademics.createClass(actor, "Rejected class", "REJECT-${UUID.randomUUID()}", 2026, "Tarde", "rejected-${UUID.randomUUID()}")
        }
        assertEquals(before, jdbc.queryForObject("select count(*) from core.classes", Int::class.java))
    }

    @Test
    fun emailConfirmationAndAdministrativeActivationUseConsistentPostgresLockOrder() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Concurrent activation","code":"CA-${UUID.randomUUID()}","year":2026,"shift":"Tarde"}"""
        }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val email = "concurrent-${UUID.randomUUID()}@example.test"
        mvc.post("/api/v1/auth/register") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            content = """{"name":"Concurrent Student","email":"$email","password":"A long test password"}"""
        }.andExpect { status { isCreated() } }
        val enrollmentResult = mvc.post("/api/v1/enrollments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "enrollment-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","email":"$email"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val enrollmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(enrollmentResult.response.contentAsString).get("id").asText()
        val token = Regex(""""token":"([^"]+)"""").find(latestOutboxPayload("identity.email-confirmation-requested"))!!.groupValues[1]
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val confirmation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/auth/email-confirmations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    content = """{"token":"$token"}"""
                }.andReturn().response.status
            }
            val activation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/enrollments/$enrollmentId/activate") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "activate-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            start.countDown()
            assertEquals(200, confirmation.get(15, TimeUnit.SECONDS))
            assertTrue(activation.get(15, TimeUnit.SECONDS) in setOf(200, 409))
            assertEquals("active", jdbc.queryForObject("select status from core.enrollments where id=?", String::class.java, enrollmentId))
            assertEquals(adminId, jdbc.queryForObject("select created_by from core.enrollments where id=?", String::class.java, enrollmentId))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun sameAdminAccountConfirmationAndClassCloseUseConsistentPostgresLockOrder() {
        val (adminCookie, adminId) = adminSession()
        val email = jdbc.queryForObject("select email from core.users where id=?", String::class.java, adminId)!!
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Same actor race","code":"SAR-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val enrollmentId = UUID.randomUUID().toString()
        val token = "same-actor-token-${UUID.randomUUID()}"
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("UPDATE core.users SET email_confirmed_at=NULL WHERE id=?", adminId)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by)
            VALUES(?,1,?,?,?,?,?,?,NULL,'pending',?)""", enrollmentId, now, now, adminId, classId, email, email.lowercase(), adminId)
        jdbc.update("""INSERT INTO core.auth_tokens(id,user_id,purpose,token_hash,email_at_issue,created_at,expires_at)
            VALUES(?,?,'email_confirmation',?,?,?,?)""", UUID.randomUUID().toString(), adminId, sha256(token), email, now, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(1800)))
        val start = java.util.concurrent.CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val confirmation = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/auth/email-confirmations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    content = """{"token":"$token"}"""
                }.andReturn().response.status
            }
            val close = executor.submit<Int> {
                start.await()
                mvc.post("/api/v1/classes/$classId/close") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "close-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", adminCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            start.countDown()
            assertEquals(200, confirmation.get(15, TimeUnit.SECONDS))
            assertEquals(200, close.get(15, TimeUnit.SECONDS))
            assertTrue(jdbc.queryForObject("select status in ('cancelled','ended') from core.enrollments where id=?", Boolean::class.java, enrollmentId)!!)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun enrollmentSearchUsesLinkedIdentityBeforeHistoricalEmailFallback() {
        val (adminCookie, adminId) = adminSession()
        val classResult = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"Search class","code":"S-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andReturn()
        val classId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classResult.response.contentAsString).get("id").asText()
        val original = "old-${UUID.randomUUID()}@example.test"
        val replacement = "new-${UUID.randomUUID()}@example.test"
        val linkedId = UUID.randomUUID().toString()
        val otherId = UUID.randomUUID().toString()
        val enrollmentId = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", linkedId, now, now, linkedId, "Linked Student", original, original, "unused", now)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by) VALUES(?,1,?,?,?,?,?,?,?,'active',?)""", enrollmentId, now, now, adminId, classId, original, original, linkedId, adminId)
        jdbc.update("UPDATE core.users SET email=?,normalized_email=?,revision=revision+1 WHERE id=?", replacement, replacement, linkedId)
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES(?,1,?,?,?,?,?,?,?,'student',true,?)""", otherId, now, now, otherId, "New Owner", original, original, "unused", now)
        mvc.get("/api/v1/enrollments?q=$original") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", adminCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].studentId") { value(linkedId) } }
    }

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
        assertEquals(10, jdbc.queryForObject("select count(*) from core.flyway_schema_history where success", Int::class.java))
    }

    private fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    private fun createB06Class(adminCookie: String): String {
        val result = mvc.post("/api/v1/classes") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "class-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"name":"B06 gate","code":"G-${UUID.randomUUID()}","year":2026,"shift":"Noite"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("id").asText()
    }

    private fun createB07WorkModule(adminCookie: String, teacherCookie: String, teacherId: String): Pair<String, String> {
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"B07 work fixture","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        return classId to moduleId
    }

    private fun createB07Enrollment(classId: String, studentId: String, createdBy: String, unassignedIds: List<String> = emptyList(), enrolledAt: java.time.Instant? = mutableClock.instant()): String {
        val id = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        val email = jdbc.queryForObject("SELECT email FROM core.users WHERE id=?", String::class.java, studentId)!!
        val ids = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(unassignedIds)
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by,enrolled_at,unassigned_assessment_ids)
            VALUES(?,1,?,?,?,?,?,?,?,'active',?,?,?::jsonb)""", id, now, now, createdBy, classId, email, email.lowercase(), studentId, createdBy, enrolledAt?.let(java.sql.Timestamp::from), ids)
        return id
    }

    private fun createB07Assessment(moduleId: String, kind: String = "work", status: String = "published", allowLate: Boolean = false, deadline: java.time.Instant? = mutableClock.instant().plusSeconds(600)): String {
        val id = "assessment-${UUID.randomUUID()}"
        val publishedAt = if (status == "published") java.sql.Timestamp.from(mutableClock.instant().minusSeconds(1)) else null
        jdbc.update("INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late) VALUES(?,?,?,?,1,?,?,?)", id, moduleId, kind, status, publishedAt, deadline?.let(java.sql.Timestamp::from), allowLate)
        return id
    }

    private fun createB06Subject(adminCookie: String, classId: String, teacherId: String): String {
        val result = mvc.post("/api/v1/subjects") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "subject-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"classId":"$classId","name":"Gate subject","teacherIds":["$teacherId"],"color":"blue","icon":"book","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString).get("id").asText()
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

    private fun awaitLockWaitCount(expected: Int) {
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

    private fun adminSession() = userSession("admin")

    private fun userSession(role: String): Pair<String, String> {
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
