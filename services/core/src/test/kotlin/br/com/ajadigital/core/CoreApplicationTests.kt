package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
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
import java.sql.Connection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import javax.sql.DataSource
import java.net.URI
import java.net.ServerSocket
import com.sun.net.httpserver.HttpServer
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
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
import br.com.ajadigital.core.adapter.out.persistence.JdbcAttendanceStore
import br.com.ajadigital.core.adapter.out.persistence.JdbcAnnouncementStore
import br.com.ajadigital.core.adapter.out.persistence.JdbcAcademicStore
import br.com.ajadigital.core.adapter.out.persistence.JdbcTopicStore
import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.ManageAcademics
import br.com.ajadigital.core.application.identity.OutboxEncryption
import br.com.ajadigital.core.ports.out.BankQuestionSnapshot
import br.com.ajadigital.core.ports.out.BankQuestionImage
import br.com.ajadigital.core.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.ports.out.IdempotencyStore

class MutableTestClock : Clock() {
    private val offset = AtomicReference(Duration.ZERO)
    private val frozenInstant = AtomicReference<Instant?>(null)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = frozenInstant.get() ?: Instant.now(Clock.systemUTC()).plus(offset.get())
    fun advance(duration: Duration) {
        if (frozenInstant.get() != null) frozenInstant.updateAndGet { it?.plus(duration) }
        else offset.updateAndGet { it.plus(duration) }
    }
    fun freeze() { frozenInstant.set(instant()) }
    fun freezeAt(value: Instant) { frozenInstant.set(value) }
    fun reset() { frozenInstant.set(null); offset.set(Duration.ZERO) }
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
            BindingBuilder.bind(eventQueue).to(exchange).with("academic.announcement-notification-requested"),
        )
    }
}

class TestQuestionSnapshotProvider : QuestionSnapshotProvider {
    val requested = mutableListOf<List<String>>()
    var imageFileId = "question-image-1"
    private val nextGate = AtomicReference<Pair<CountDownLatch, CountDownLatch>?>(null)
    fun blockNextSnapshot(started: CountDownLatch, release: CountDownLatch) { nextGate.set(started to release) }
    override fun snapshots(ids: List<String>): List<BankQuestionSnapshot> {
        requested += ids
        nextGate.getAndSet(null)?.let { (started, release) ->
            started.countDown()
            check(release.await(20, TimeUnit.SECONDS)) { "Snapshot test gate was not released" }
        }
        if ("offline" in ids) throw br.com.ajadigital.core.application.academic.QuestionsUnavailable()
        return ids.map { id -> BankQuestionSnapshot(id, 3, "a".repeat(64), "Math", "Algebra", "easy", "AJA", 2026, "Original $id", listOf("one", "two"), 1, "Because", if (id == "bank-1") BankQuestionImage(imageFileId, "Figure") else null) }
    }
}

@TestConfiguration
class AssessmentSnapshotTestConfiguration {
    @Bean
    @Primary
    fun testQuestionSnapshotProvider() = TestQuestionSnapshotProvider()
}

@Testcontainers
@AutoConfigureMockMvc
@Import(MutableTestClockConfiguration::class, EmailQueueTopologyTestConfiguration::class, AssessmentSnapshotTestConfiguration::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoreApplicationTests @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val flyway: Flyway,
    private val mvc: MockMvc,
    private val rabbitTemplate: RabbitTemplate,
    private val outboxPublisher: OutboxPublisher,
    private val passwordEncoder: PasswordEncoder,
    private val mutableClock: MutableTestClock,
    private val manageAcademics: ManageAcademics,
    private val idempotencyStore: IdempotencyStore,
    private val questionSnapshotProvider: TestQuestionSnapshotProvider,
    private val webContext: ServletWebServerApplicationContext,
) {
    @BeforeEach
    fun resetClock() = mutableClock.reset()

    @Test
    fun dashboardReturnsAnEmptyRoleProjectionForAnUnassignedAdmin() {
        val (cookie, _) = adminSession()

        val result = mvc.get("/api/v1/me/dashboard") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", cookie))
        }.andExpect { status { isOk() } }.andReturn()

        val dashboard = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.response.contentAsString)
        assertEquals("admin", dashboard.get("role").asText())
        assertTrue(dashboard.get("pendingEnrollmentCount").intValue() >= 0)
        assertTrue(dashboard.get("studentCount").intValue() >= 0)
        assertTrue(dashboard.get("pendingFeedbackCount").intValue() >= 0)
        listOf("subjects", "classes", "assessments", "announcements", "upcoming", "topics").forEach { assertTrue(dashboard.get(it).isArray) }
    }

    @Test
    fun dashboardReturnsScopedEmptyAndAssignedProjectionsAndRejectsRevokedSessions() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (unassignedTeacherCookie, _) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (unassignedStudentCookie, _) = userSession("student")

        fun dashboard(cookie: String) = tools.jackson.databind.json.JsonMapper.builder().build()
            .readTree(liveHttp("GET", "/me/dashboard", cookie).body())

        val noClassStudent = dashboard(unassignedStudentCookie)
        assertEquals("student", noClassStudent.get("role").asText())
        assertTrue(noClassStudent.get("subjects").isEmpty)
        assertTrue(noClassStudent.get("assessments").isEmpty)
        assertTrue(noClassStudent.get("announcements").isEmpty)
        assertTrue(noClassStudent.get("upcoming").isEmpty)
        assertTrue(noClassStudent.get("progress").isEmpty)
        assertTrue(noClassStudent.get("topics").isEmpty)
        assertTrue(noClassStudent.get("resume").isNull)
        assertEquals(0, noClassStudent.get("totalMaterials").intValue())

        val noSubjectTeacher = dashboard(unassignedTeacherCookie)
        assertEquals("teacher", noSubjectTeacher.get("role").asText())
        assertTrue(noSubjectTeacher.get("subjects").isEmpty)
        assertTrue(noSubjectTeacher.get("classes").isEmpty)
        assertTrue(noSubjectTeacher.get("assessments").isEmpty)
        assertTrue(noSubjectTeacher.get("topics").isEmpty)

        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "dashboard-module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Dashboard module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        createB07Assessment(moduleId)
        val studentSubjects = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/subjects?page=1&pageSize=100", studentCookie).body(),
        ).get("items")
        val studentDashboard = dashboard(studentCookie)
        assertEquals(setOf(subjectId), studentDashboard.get("subjects").mapNotNull { it.get("id")?.asText() }.toSet(), studentDashboard.toString())
        assertEquals(studentSubjects.size(), studentDashboard.get("subjects").size())
        val studentAssessments = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/assessments?page=1&pageSize=100&status=published", studentCookie).body(),
        ).get("items")
        assertEquals(studentAssessments.mapNotNull { it.get("id")?.asText() }.toSet(), studentDashboard.get("assessments").mapNotNull { it.get("id")?.asText() }.toSet(), "source=${studentAssessments} dashboard=${studentDashboard.get("assessments")}")
        assertEquals(studentAssessments.size(), studentDashboard.get("pendingAssessmentCount").intValue())
        val progressTotal = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/me/progress?page=1&pageSize=1", studentCookie).body(),
        ).get("total").intValue()
        val materialTotal = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/materials?page=1&pageSize=1", studentCookie).body(),
        ).get("total").intValue()
        assertEquals(progressTotal, studentDashboard.get("completedMaterials").intValue())
        assertEquals(materialTotal, studentDashboard.get("totalMaterials").intValue())

        val teacherSubjects = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/subjects?page=1&pageSize=100", teacherCookie).body(),
        ).get("items")
        val teacherDashboard = dashboard(teacherCookie)
        assertEquals(subjectId, teacherDashboard.get("subjects").single().get("id")?.asText(), teacherDashboard.toString())
        assertEquals(teacherSubjects.size(), teacherDashboard.get("subjects").size())

        val adminClasses = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/classes?page=1&pageSize=100&active=true", adminCookie).body(),
        ).get("items")
        val adminDashboard = dashboard(adminCookie)
        assertTrue(adminDashboard.get("classes").any { it.get("id").asText() == classId })
        assertEquals(adminClasses.size(), adminDashboard.get("classes").size())
        val dashboardClass = adminDashboard.get("classes").first { it.get("id").asText() == classId }
        assertTrue(dashboardClass.has("studentCount"), dashboardClass.toString())
        assertEquals(1, dashboardClass.get("studentCount").intValue())
        val studentCount = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/students?page=1&pageSize=1", adminCookie).body(),
        ).get("total").intValue()
        assertEquals(studentCount, adminDashboard.get("studentCount").intValue())
        val pendingEnrollments = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/enrollments?page=1&pageSize=1&status=pending", adminCookie).body(),
        ).get("total").intValue()
        assertEquals(pendingEnrollments, adminDashboard.get("pendingEnrollmentCount").intValue())
        val submissions = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/submissions?page=1&pageSize=100", adminCookie).body(),
        ).get("items")
        val pendingFeedback = submissions.count { it.get("submittedAt") != null && !it.get("submittedAt").isNull && it.get("feedbackPublished").isNull }
        assertEquals(pendingFeedback, adminDashboard.get("pendingFeedbackCount").intValue())

        val (revokedCookie, _) = userSession("student")
        assertEquals(200, liveHttp("GET", "/me/dashboard", revokedCookie).statusCode())
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(revokedCookie))
        assertEquals(401, liveHttp("GET", "/me/dashboard", revokedCookie).statusCode())
    }

    @Test
    fun dashboardForumProjectionIsBoundedAndContainsOnlySummaryFields() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val create = liveHttp(
            "POST", "/topics", teacherCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Dashboard topic","body":"Private body","tags":[],"target":null,"attachmentId":null}""",
            "dashboard-topic-${UUID.randomUUID()}",
        )
        assertEquals(201, create.statusCode(), create.body())

        val dashboard = tools.jackson.databind.json.JsonMapper.builder().build()
            .readTree(liveHttp("GET", "/me/dashboard", teacherCookie).body())
        val topic = dashboard.get("topics").single()
        assertEquals("Dashboard topic", topic.get("title").asText())
        assertEquals("/forum/topico?id=${topic.get("id").asText()}", topic.get("href").asText())
        assertEquals(5, topic.size())
        assertFalse(topic.has("body"))
        assertFalse(topic.has("replies"))
        assertFalse(topic.has("author"))

        val delegate = checkNotNull(jdbc.dataSource)
        val queryCount = java.util.concurrent.atomic.AtomicInteger()
        val countingDataSource = object : DataSource by delegate {
            override fun getConnection(): Connection {
                val connection = delegate.connection
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                    if (method.name == "prepareStatement") queryCount.incrementAndGet()
                    try { method.invoke(connection, *(args ?: emptyArray())) }
                    catch (error: InvocationTargetException) { throw error.targetException }
                } as Connection
            }
            override fun getConnection(username: String, password: String): Connection = getConnection()
        }
        val store = JdbcTopicStore(JdbcTemplate(countingDataSource), tools.jackson.databind.json.JsonMapper.builder().build(), mutableClock, idempotencyStore)
        assertEquals(1, store.dashboardTopics(teacherId, "teacher", sha256(teacherCookie), 5).size)
        assertTrue(queryCount.get() <= 2, "Expected at most two SQL statements for the scoped topic summary, observed ${queryCount.get()}")
    }

    @Test
    fun studentDashboardRequiresEveryFieldAndUsesExactAggregatesBeyondTheFirstHundredRows() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val invitationClassId = createB06Class(adminCookie)
        val studentEmail = jdbc.queryForObject("SELECT email FROM core.users WHERE id=?", String::class.java, studentId)!!
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,status,created_by,unassigned_assessment_ids)
            VALUES(?,1,?,?,?,?,?,?,'pending',?,'[]'::jsonb)""", UUID.randomUUID().toString(), now, now, adminId, invitationClassId, studentEmail, studentEmail.lowercase(), adminId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "dashboard-large-module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Large dashboard module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val moduleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        repeat(101) { createB07Assessment(moduleId) }

        repeat(101) { index ->
            val materialId = "dashboard-material-${index.toString().padStart(3, '0')}"
            val progressId = "dashboard-progress-${(if (index == 100) 0 else index + 1).toString().padStart(3, '0')}"
            jdbc.update("""INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index)
                VALUES(?,1,?,?,?,?,?,?, 'lesson',1,'',NULL,NULL,?)""", materialId, now, now, teacherId, teacherId, moduleId, "Material $index", index)
            jdbc.update("""INSERT INTO core.material_progress(id,revision,created_at,updated_at,updated_by,user_id,material_id,completed,last_access)
                VALUES(?,1,?,?,?,?,?,?,?)""", progressId, now, now, studentId, studentId, materialId, index == 0, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(if (index == 100) 200 else index.toLong())))
        }

        val response = liveHttp("GET", "/me/dashboard", studentCookie)
        assertEquals(200, response.statusCode(), response.body())
        val dashboard = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body())
        val myEnrollments = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/me/enrollments?page=1&pageSize=100", studentCookie).body(),
        )
        val pendingEnrollmentCount = myEnrollments.get("items").count { it.get("status").asText() == "pending" }
        assertAll(
            "student dashboard contract and aggregates",
            { assertTrue(dashboard.has("pendingEnrollmentCount"), dashboard.toString()) },
            { assertEquals(pendingEnrollmentCount, dashboard.get("pendingEnrollmentCount")?.intValue()) },
            { assertEquals(1, dashboard.get("completedMaterials")?.intValue()) },
            { assertEquals(101, dashboard.get("totalMaterials")?.intValue()) },
            { assertEquals("dashboard-material-100", dashboard.get("resume")?.get("id")?.asText()) },
            { assertEquals(101, dashboard.get("pendingAssessmentCount")?.intValue()) },
            { assertTrue(dashboard.get("assessments").size() <= 100) },
            { assertTrue(dashboard.get("assessments").all { it.has("questionCount") && it.get("status").asText() == "published" && !it.get("publishedAt").isNull }) },
        )
    }

    @Test
    fun staffDashboardCountsAllPendingFeedbackAndOnlyShowsPublishedAssessments() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "dashboard-staff-module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"Staff dashboard module","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val dashboardModuleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        repeat(101) { index ->
            val assessmentId = createB07Assessment(dashboardModuleId)
            val now = java.sql.Timestamp.from(mutableClock.instant())
            jdbc.update("""INSERT INTO core.submissions(id,revision,created_at,updated_at,updated_by,assessment_id,user_id,submitted_at,receipt_id)
                VALUES(?,1,?,?,?,?,?,?,?)""", "dashboard-submission-$index-${UUID.randomUUID()}", now, now, studentId, assessmentId, studentId, now, "dashboard-receipt-${UUID.randomUUID()}")
        }
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description)
            VALUES('000-dashboard-draft',?, 'work','draft',1,NULL,NULL,false,?,?,?,'Dashboard draft','')""", dashboardModuleId, now, now, teacherId)

        val response = liveHttp("GET", "/me/dashboard", adminCookie)
        assertEquals(200, response.statusCode(), response.body())
        val dashboard = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body())
        val sourceList = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/assessments?status=published", adminCookie).body())
        val staffAssessmentFields = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "moduleId", "title", "description", "kind", "maxGrade", "deadline", "required", "allowLate", "status", "publishedAt", "questionIds", "questions", "hasSubmissions")
        assertTrue(sourceList.get("items").all { it.properties().map { field -> field.key }.toSet() == staffAssessmentFields }, sourceList.toString())
        val sourceDetail = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/assessments/${dashboard.get("assessments").get(0).get("id").asText()}", adminCookie).body())
        assertEquals(staffAssessmentFields, sourceDetail.properties().map { it.key }.toSet(), sourceDetail.toString())
        val mutation = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "dashboard-assessment-dto-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$dashboardModuleId","title":"DTO compatibility","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val mutationDto = tools.jackson.databind.json.JsonMapper.builder().build().readTree(mutation.response.contentAsString)
        assertEquals(staffAssessmentFields, mutationDto.properties().map { it.key }.toSet(), mutationDto.toString())
        val pendingFeedback = jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE submitted_at IS NOT NULL AND feedback_published IS NULL", Long::class.java)!!.toInt()
        assertAll(
            "staff dashboard contract and aggregates",
            { assertEquals(pendingFeedback, dashboard.get("pendingFeedbackCount").intValue()) },
            { assertTrue(dashboard.get("assessments").all { it.get("status").asText() == "published" }) },
            { assertTrue(dashboard.get("assessments").all { it.has("questionCount") }) },
        )
    }

    @Test
    fun dashboardUpcomingUsesSaoPauloCalendarDateAtUtcBoundary() {
        mutableClock.freezeAt(Instant.parse("2026-11-13T01:00:00Z"))
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val create = liveHttp("POST", "/announcements", teacherCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Local today","body":"Still upcoming","notify":false,"occurrence":{"date":"2026-11-12","time":"22:30","allDay":false}}""",
            "dashboard-local-date-${UUID.randomUUID()}")
        assertEquals(201, create.statusCode(), create.body())
        val dashboard = tools.jackson.databind.json.JsonMapper.builder().build()
            .readTree(liveHttp("GET", "/me/dashboard", studentCookie).body())
        assertTrue(dashboard.get("upcoming").any { it.get("title").asText() == "Local today" }, dashboard.toString())
    }

    @Test
    fun dashboardAnnouncementAndCalendarQueriesBoundRowsInPostgres() {
        mutableClock.freezeAt(Instant.parse("2026-11-12T15:00:00Z"))
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val now = java.sql.Timestamp.from(mutableClock.instant())
        repeat(120) { index ->
            val id = "dashboard-event-${index.toString().padStart(3, '0')}"
            val occurrence = """{"date":"2026-11-12","time":"${(index % 24).toString().padStart(2, '0')}:00","allDay":false}"""
            jdbc.update("""INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence)
                VALUES(?,1,?,?,?,?,?,?,?,?,'false',?::jsonb)""", id, now, now, adminId, adminId, classId, subjectId, "Event $index", "Body", occurrence)
        }
        val capturedSql = mutableListOf<String>()
        val delegate = checkNotNull(jdbc.dataSource)
        val countingDataSource = object : DataSource by delegate {
            override fun getConnection(): Connection {
                val connection = delegate.connection
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                    if (method.name == "prepareStatement") capturedSql += args?.get(0) as String
                    try { method.invoke(connection, *(args ?: emptyArray())) }
                    catch (error: InvocationTargetException) { throw error.targetException }
                } as Connection
            }
            override fun getConnection(username: String, password: String): Connection = getConnection()
        }
        val store = JdbcAnnouncementStore(
            JdbcTemplate(countingDataSource), tools.jackson.databind.json.JsonMapper.builder().build(), mutableClock,
            OutboxEncryption(java.util.Base64.getEncoder().encodeToString(ByteArray(32))),
        )
        val sessionHash = sha256(studentCookie)
        repeat(3) { createB06Class(adminCookie) }
        capturedSql.clear()
        val classPage = JdbcAcademicStore(JdbcTemplate(countingDataSource), mutableClock).dashboardClasses(adminId, "admin", 100)
        assertTrue(classPage.items.size >= 3)
        assertTrue(classPage.items.all { it.containsKey("studentCount") })
        val classQueries = capturedSql.filter { it.contains("FROM core.classes", ignoreCase = true) }
        assertEquals(1, classQueries.size, classQueries.toString())
        assertTrue(Regex("\\bLIMIT\\s+\\?", RegexOption.IGNORE_CASE).containsMatchIn(classQueries.single()))
        capturedSql.clear()
        val notices = store.dashboardAnnouncements(studentId, "student", sessionHash, 5)
        val upcoming = store.dashboardUpcoming(studentId, "student", sessionHash, "2026-11-12", "2026-12-12", 5)
        assertEquals(5, notices.size)
        assertEquals(5, upcoming.size)
        val announcementQueries = capturedSql.filter { it.contains("FROM core.announcements", ignoreCase = true) }
        assertEquals(2, announcementQueries.size, announcementQueries.toString())
        assertTrue(announcementQueries.all { Regex("\\bLIMIT\\s+\\?", RegexOption.IGNORE_CASE).containsMatchIn(it) }, announcementQueries.joinToString("\n"))
    }

    @Test
    fun announcementIsVisibleOnlyToItsClassAndAppearsInCalendar() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (unassignedTeacherCookie, _) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val beforeEvents = jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java)!!
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val createBody = """{"classId":"$classId","subjectId":"$subjectId","title":"Class update","body":"Tomorrow","notify":true,"occurrence":{"date":"2026-11-12","time":"10:30","allDay":false}}"""
        val createKey = "announcement-${UUID.randomUUID()}"
        val response = liveHttp("POST", "/announcements", teacherCookie, createBody, createKey)
        assertEquals(201, response.statusCode(), response.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).get("id").asText()

        assertEquals(200, liveHttp("GET", "/announcements/$id", studentCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/announcements/$id", outsiderCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/announcements/$id", unassignedTeacherCookie).statusCode())
        val list = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/announcements?classId=$classId&subjectId=$subjectId&q=update", studentCookie).body())
        assertEquals(id, list.get("items").get(0).get("id").asText())
        val calendar = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/calendar?from=2026-11-12&to=2026-11-12", studentCookie).body())
        assertEquals("announcement", calendar.get("items").get(0).get("source").asText())
        assertEquals(id, calendar.get("items").get(0).get("sourceId").asText())
        assertEquals("10:30", calendar.get("items").get(0).get("time").asText())

        val events = jdbc.queryForObject(
            "SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'",
            Int::class.java,
        )
        assertEquals(beforeEvents + 1, events)
        val recipients = jdbc.queryForObject(
            "SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND recipient_id=?",
            Int::class.java,
            id,
            studentId,
        )
        assertEquals(1, recipients)
        val eventId = jdbc.queryForObject("SELECT event_id FROM core.announcement_notification_recipients WHERE announcement_id=? AND revision=1 AND recipient_id=?", String::class.java, id, studentId)!!
        awaitPublished(eventId)
        val envelope = tools.jackson.databind.json.JsonMapper.builder().build().readTree(receivePublishedEvent(eventId))
        assertEquals("academic.announcement-notification-requested", envelope.get("eventType").asText())
        val payload = tools.jackson.databind.json.JsonMapper.builder().build().readTree(outboxPayload(eventId, "academic.announcement-notification-requested"))
        assertEquals(id, payload.get("announcementId").asText())
        assertEquals(studentId, payload.get("recipientId").asText())
        assertEquals(1, payload.get("revision").intValue())
        val replay = liveHttp("POST", "/announcements", teacherCookie, createBody, createKey)
        assertEquals(201, replay.statusCode())
        assertEquals(id, tools.jackson.databind.json.JsonMapper.builder().build().readTree(replay.body()).get("id").asText())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND revision=1", Int::class.java, id))
        val noOp = liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":1}""")
        assertEquals(200, noOp.statusCode(), noOp.body())
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(noOp.body()).get("revision").intValue())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=?", Int::class.java, id))
        val edited = liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":1,"title":"Updated","notify":true}""")
        assertEquals(200, edited.statusCode(), edited.body())
        val versionTwoEvent = jdbc.queryForObject("SELECT event_id FROM core.announcement_notification_recipients WHERE announcement_id=? AND revision=2 AND recipient_id=?", String::class.java, id, studentId)!!
        awaitPublished(versionTwoEvent)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND revision=2", Int::class.java, id))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=?", Int::class.java, id))
        val classScoped = liveHttp("POST", "/announcements", teacherCookie,
            """{"classId":"$classId","subjectId":null,"title":"Whole class","body":"Update","notify":true,"occurrence":null}""",
            "class-announcement-${UUID.randomUUID()}")
        assertEquals(201, classScoped.statusCode(), classScoped.body())
        val classNoticeId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(classScoped.body()).get("id").asText()
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND recipient_id=?", Int::class.java, classNoticeId, studentId))
    }

    @Test
    fun concurrentTopicRepliesAreBothPersisted() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Help","body":"Need help","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Teacher reply"}""", "reply-teacher-${UUID.randomUUID()}") }
            val second = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", studentCookie, """{"body":"Student reply"}""", "reply-student-${UUID.randomUUID()}") }
            assertEquals(201, first.get(10, TimeUnit.SECONDS).statusCode())
            assertEquals(201, second.get(10, TimeUnit.SECONDS).statusCode())
        } finally {
            pool.shutdownNow()
        }
        val topic = liveHttp("GET", "/topics/$topicId", studentCookie)
        assertEquals(200, topic.statusCode())
        assertEquals(2, tools.jackson.databind.json.JsonMapper.builder().build().readTree(topic.body()).get("replies").size())
    }

    @Test
    fun forumReadIsPerUserNewRepliesRenewUnreadAndVotesAreIdempotent() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (readerCookie, readerId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        val title = "Du\u0301vida inicial"
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"$title","body":"Como resolver?","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        fun topicPage(unread: Boolean) = tools.jackson.databind.json.JsonMapper.builder().build().readTree(
            liveHttp("GET", "/topics?classId=$classId&q=duvida&unread=$unread", readerCookie).body(),
        )
        assertEquals(422, liveHttp("GET", "/topics?unknown=value", readerCookie).statusCode())
        assertEquals(401, liveHttp("GET", "/topics?unknown=value", "invalid-session").statusCode())
        assertEquals(1, topicPage(true).get("total").asInt())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}").statusCode())
        assertEquals(0, topicPage(true).get("total").asInt())

        val reply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Try this method"}""", "reply-${UUID.randomUUID()}")
        assertEquals(201, reply.statusCode(), reply.body())
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(reply.body()).get("replies").get(0).get("id").asText()
        assertEquals(403, liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", teacherCookie, """{"voted":true}""").statusCode())
        assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        assertEquals(1, topicPage(true).get("total").asInt())
        val voted = liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", readerCookie, """{"voted":true}""")
        assertEquals(200, voted.statusCode(), voted.body())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/vote", readerCookie, """{"voted":true}""").statusCode())
        val vote = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("replies").get(0)
        assertEquals(1, vote.get("voteCount").asInt())
        assertEquals(true, vote.get("votedByMe").asBoolean())

        val solved = liveHttp("POST", "/topics/$topicId/solution", authorCookie, """{"expectedRevision":1,"replyId":"$replyId"}""")
        assertEquals(200, solved.statusCode(), solved.body())
        assertEquals(true, tools.jackson.databind.json.JsonMapper.builder().build().readTree(solved.body()).get("replies").get(0).get("accepted").asBoolean())
        val withdrawn = liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""")
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(withdrawn.body()).get("replies").size())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?classId=$classId&solved=true", readerCookie).body()).get("total").asInt())
    }

    @Test
    fun forumReplyRequiresIdempotencyAndReplaysOnlyTheSameIntent() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Reply key","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val body = """{"body":"One answer"}"""
        assertEquals(422, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body).statusCode())
        val key = "reply-${UUID.randomUUID()}"
        val pool = Executors.newFixedThreadPool(2)
        val firstFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key) }
        val replayFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key) }
        val first = firstFuture.get(10, TimeUnit.SECONDS)
        val replay = replayFuture.get(10, TimeUnit.SECONDS)
        pool.shutdownNow()
        assertEquals(201, first.statusCode(), first.body())
        assertEquals(first.body(), replay.body())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topic_replies WHERE topic_id=?", Int::class.java, topicId))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_reply_created' AND subject_user_id=(SELECT id FROM core.topic_replies WHERE topic_id=?)", Int::class.java, topicId))
        assertEquals(409, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Different answer"}""", key).statusCode())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topic_replies WHERE topic_id=?", Int::class.java, topicId))
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(teacherCookie))
        assertEquals(401, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, body, key).statusCode())
    }

    @Test
    fun forumSearchMatchesOnlyVisibleReplyTextAndWithdrawalKeepsAudit() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"General question","body":"No matching phrase","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val reply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Distinctive reply phrase"}""", "reply-${UUID.randomUUID()}")
        assertEquals(201, reply.statusCode(), reply.body())
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(reply.body()).get("replies").get(0).get("id").asText()
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?q=distinctive", studentCookie).body()).get("total").asInt())
        assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""").statusCode())
        assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?q=distinctive", studentCookie).body()).get("total").asInt())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_reply_withdrawn' AND subject_user_id=?", Int::class.java, replyId))
    }

    @Test
    fun forumReadHoldsTheTopicSnapshotUntilItsWatermarkCommits() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (readerCookie, readerId) = userSession("student")
        val (authorCookie, authorId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Snapshot race","body":"Initial","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val gate = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        gate.createStatement().use { it.execute("SELECT pg_advisory_lock(7712345)") }
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""CREATE OR REPLACE FUNCTION core.pause_forum_read() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.topic_id='$topicId' AND NEW.user_id='$readerId' THEN PERFORM pg_advisory_xact_lock(7712345); END IF; RETURN NEW; END ${'$'}${'$'}""")
                statement.execute("CREATE TRIGGER pause_forum_read BEFORE INSERT OR UPDATE ON core.topic_reads FOR EACH ROW EXECUTE FUNCTION core.pause_forum_read()")
            }
        }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val readFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}") }
            awaitLockWait("topic_reads")
            val replyFuture = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Arrived after snapshot"}""", "reply-${UUID.randomUUID()}") }
            awaitLockWaitCount(2)
            gate.createStatement().use { it.execute("SELECT pg_advisory_unlock(7712345)") }
            val read = readFuture.get(10, TimeUnit.SECONDS)
            val reply = replyFuture.get(10, TimeUnit.SECONDS)
            assertEquals(200, read.statusCode(), read.body())
            assertEquals(201, reply.statusCode(), reply.body())
            assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(read.body()).get("replies").size())
            assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        } finally {
            gate.createStatement().use { it.execute("SELECT pg_advisory_unlock(7712345)") }
            gate.close()
            pool.shutdownNow()
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP TRIGGER IF EXISTS pause_forum_read ON core.topic_reads")
                    statement.execute("DROP FUNCTION IF EXISTS core.pause_forum_read()")
                }
            }
        }
    }

    @Test
    fun forumMaterialReferenceCannotCrossClassOrSubjectContext() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classA, moduleA) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (classB, moduleB) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val subjectA = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleA)!!
        val subjectB = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleB)!!
        val validMaterialId = UUID.randomUUID().toString()
        val otherMaterialId = UUID.randomUUID().toString()
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?, 'lesson',1,'',NULL,NULL,0)", validMaterialId, now, now, teacherId, teacherId, moduleA, "Local material")
        jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?, 'lesson',1,'',NULL,NULL,0)", otherMaterialId, now, now, teacherId, teacherId, moduleB, "Other material")
        val validKey = "topic-material-${UUID.randomUUID()}"
        val validBody = """{"classId":"$classA","subjectId":"$subjectA","title":"Local reference","body":"Body","tags":[],"target":{"type":"material","id":"$validMaterialId"},"attachmentId":null}"""
        val valid = liveHttp("POST", "/topics", adminCookie, validBody, validKey)
        assertEquals(201, valid.statusCode(), valid.body())
        val crossContext = liveHttp("POST", "/topics", adminCookie,
            """{"classId":"$classA","subjectId":"$subjectA","title":"Foreign reference","body":"Body","tags":[],"target":{"type":"material","id":"$otherMaterialId"},"attachmentId":null}""",
            "topic-material-${UUID.randomUUID()}")
        assertEquals(422, crossContext.statusCode(), crossContext.body())
        jdbc.update("DELETE FROM core.materials WHERE id=?", validMaterialId)
        val replayAfterMaterialWithdrawn = liveHttp("POST", "/topics", adminCookie, validBody, validKey)
        assertEquals(201, replayAfterMaterialWithdrawn.statusCode(), replayAfterMaterialWithdrawn.body())
        assertEquals(valid.body(), replayAfterMaterialWithdrawn.body())
        assertEquals(classA, tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("classId").asText())
        assertTrue(subjectA != subjectB && classA != classB)
    }

    @Test
    fun forumReadWatermarkNeverAdvancesBeyondTheVisibleActivity() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Read watermark","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()

        mutableClock.advance(Duration.ofMinutes(1))
        mutableClock.freeze()
        try {
            assertEquals(200, liveHttp("POST", "/topics/$topicId/read", studentCookie, "{}").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", teacherCookie,
                """{"body":"Reply at the same clock instant"}""", "reply-${UUID.randomUUID()}").statusCode())
            val afterReply = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", studentCookie).body())
            assertEquals(false, afterReply.get("read").asBoolean())
            assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics?unread=true", studentCookie).body()).get("total").asInt())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun forumReplyActivityAdvancesWhenClockDoesNotMove() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (readerCookie, readerId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, readerId, teacherId)
        mutableClock.freeze()
        try {
            val created = liveHttp("POST", "/topics", authorCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Equal clock","body":"Question","tags":[],"target":null,"attachmentId":null}""",
                "topic-${UUID.randomUUID()}")
            val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
            val initialActivity = jdbc.queryForObject("SELECT last_activity_at FROM core.topics WHERE id=?", java.time.Instant::class.java, topicId)!!
            assertEquals(200, liveHttp("POST", "/topics/$topicId/read", readerCookie, "{}").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", teacherCookie,
                """{"body":"At unchanged clock"}""", "reply-${UUID.randomUUID()}").statusCode())
            val replyActivity = jdbc.queryForObject("SELECT last_activity_at FROM core.topics WHERE id=?", java.time.Instant::class.java, topicId)!!
            assertTrue(replyActivity.isAfter(initialActivity), "A reply must advance activity even when the clock is frozen")
            assertEquals(false, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", readerCookie).body()).get("read").asBoolean())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun replyWithdrawalDoesNotHideFutureSameClockReplyAsRead() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (authorCookie, authorId) = userSession("student")
        val (secondStudentCookie, secondStudentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, authorId, teacherId)
        createB07Enrollment(classId, secondStudentId, teacherId)
        val created = liveHttp("POST", "/topics", authorCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawal watermark","body":"Question","tags":[],"target":null,"attachmentId":null}""",
            "topic-${UUID.randomUUID()}")
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val firstReply = liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Withdraw me"}""", "reply-${UUID.randomUUID()}")
        val replyId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(firstReply.body()).get("replies").get(0).get("id").asText()
        mutableClock.advance(Duration.ofMinutes(1))
        mutableClock.freeze()
        try {
            assertEquals(200, liveHttp("POST", "/topics/$topicId/replies/$replyId/withdraw", teacherCookie, """{"expectedRevision":1}""").statusCode())
            assertEquals(201, liveHttp("POST", "/topics/$topicId/replies", secondStudentCookie,
                """{"body":"New reply after withdrawal"}""", "reply-${UUID.randomUUID()}").statusCode())
            val afterReply = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/topics/$topicId", teacherCookie).body())
            assertEquals(false, afterReply.get("read").asBoolean())
        } finally {
            mutableClock.reset()
        }
    }

    @Test
    fun questionSnapshotRpcDoesNotBlockCoreAuthMutationAndCreateRechecksAfterward() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        questionSnapshotProvider.blockNextSnapshot(started, release)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val topicRequest = pool.submit<HttpResponse<String>> {
                liveHttp("POST", "/topics", studentCookie,
                    """{"classId":"$classId","subjectId":"$subjectId","title":"Blocked snapshot","body":"Question","tags":[],"target":{"type":"question","id":"bank-blocked"},"attachmentId":null}""",
                    "topic-blocked-${UUID.randomUUID()}")
            }
            assertTrue(started.await(10, TimeUnit.SECONDS), "Question provider did not enter the blocking gate")
            val logout = pool.submit<HttpResponse<String>> { liveHttp("POST", "/auth/logout", studentCookie) }
            val logoutResponse = logout.get(3, TimeUnit.SECONDS)
            assertEquals(204, logoutResponse.statusCode(), logoutResponse.body())
            release.countDown()
            val topicResponse = topicRequest.get(10, TimeUnit.SECONDS)
            assertEquals(403, topicResponse.statusCode(), topicResponse.body())
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE title='Blocked snapshot'", Int::class.java))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun topicReplayRechecksSessionAfterWaitingForIdempotencyLock() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val body = """{"classId":"$classId","subjectId":"$subjectId","title":"Replay auth race","body":"Question","tags":[],"target":null,"attachmentId":null}"""
        val key = "topic-auth-race-${UUID.randomUUID()}"
        val created = liveHttp("POST", "/topics", studentCookie, body, key)
        assertEquals(201, created.statusCode(), created.body())

        val lockConnection = jdbc.dataSource!!.connection
        val actorLock = lockConnection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")
        lockConnection.autoCommit = false
        actorLock.setString(1, "$studentId|createTopic|/topics|$key")
        actorLock.execute()
        val lockPid = lockConnection.createStatement().executeQuery("SELECT pg_backend_pid()").use { rs -> rs.next(); rs.getInt(1) }
        val pool = Executors.newSingleThreadExecutor()
        try {
            val replay = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var waiting = false
            while (System.nanoTime() < deadline && !waiting) {
                waiting = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_stat_activity a WHERE ?=ANY(pg_blocking_pids(a.pid)) AND a.wait_event_type='Lock')",
                    Boolean::class.java, lockPid,
                ) ?: false
                if (!waiting) Thread.sleep(25)
            }
            assertTrue(waiting, "Replay did not block on the held per-key idempotency lock")
            jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(studentCookie))
            lockConnection.commit()
            val replayResponse = replay.get(10, TimeUnit.SECONDS)
            assertTrue(replayResponse.statusCode() == 401 || replayResponse.statusCode() == 403, replayResponse.body())
        } finally {
            runCatching { lockConnection.rollback() }
            actorLock.close()
            lockConnection.close()
            pool.shutdownNow()
        }
    }

    @Test
    fun concurrentQuestionTopicCreateWithSameKeyCommitsOnlyOnce() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val body = """{"classId":"$classId","subjectId":"$subjectId","title":"Concurrent question create","body":"Question","tags":[],"target":{"type":"question","id":"bank-race"},"attachmentId":null}"""
        val key = "topic-race-${UUID.randomUUID()}"
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        questionSnapshotProvider.blockNextSnapshot(started, release)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val blockedRequest = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            assertTrue(started.await(10, TimeUnit.SECONDS), "First create did not enter the provider")
            val concurrentRequest = pool.submit<HttpResponse<String>> { liveHttp("POST", "/topics", studentCookie, body, key) }
            val concurrent = concurrentRequest.get(3, TimeUnit.SECONDS)
            assertEquals(201, concurrent.statusCode(), concurrent.body())
            release.countDown()
            val replay = blockedRequest.get(10, TimeUnit.SECONDS)
            assertEquals(201, replay.statusCode(), replay.body())
            assertEquals(concurrent.body(), replay.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE title='Concurrent question create'", Int::class.java))
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.audit_events WHERE event_type='academic.topic_created' AND subject_user_id=(SELECT id FROM core.topics WHERE title='Concurrent question create')", Int::class.java))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun closedClassAllowsTeamReadOnlyAndRevocationHidesTopicAndAttachment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val subjectId = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleId)!!
        val enrollmentId = createB07Enrollment(classId, studentId, teacherId)
        val upload = mvc.perform(multipart("/api/v1/files")
            .file(MockMultipartFile("file", "forum.pdf", "application/pdf", "%PDF-1.4\n".toByteArray()))
            .param("purpose", "forum").param("parentId", subjectId)
            .cookie(Cookie("aja_session", studentCookie)).header("Origin", "https://aja.example.test")
            .header("Idempotency-Key", "forum-file-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(upload.response.contentAsString).get("id").asText()
        val created = liveHttp("POST", "/topics", studentCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Attachment","body":"See file","tags":[],"target":null,"attachmentId":"$fileId"}""",
            "topic-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        assertEquals(200, liveHttp("GET", "/files/$fileId/content", teacherCookie).statusCode())

        jdbc.update("UPDATE core.classes SET active=false WHERE id=?", classId)
        assertEquals(200, liveHttp("GET", "/topics/$topicId", teacherCookie).statusCode())
        assertEquals(403, liveHttp("POST", "/topics/$topicId/replies", teacherCookie, """{"body":"Closed"}""", "reply-closed-${UUID.randomUUID()}").statusCode())
        assertEquals(404, liveHttp("GET", "/topics/$topicId", studentCookie).statusCode())
        jdbc.update("UPDATE core.classes SET active=true WHERE id=?", classId)
        jdbc.update("UPDATE core.enrollments SET status='ended' WHERE id=?", enrollmentId)
        assertEquals(404, liveHttp("GET", "/topics/$topicId", studentCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/files/$fileId/content", studentCookie).statusCode())
        assertEquals(200, liveHttp("GET", "/files/$fileId/content", teacherCookie).statusCode())
    }

    @Test
    fun questionReferenceUsesAuthenticatedSnapshotHttpProviderInFreshCoreProcess() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val tokens = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/internal/v1/snapshots") { exchange ->
            tokens += exchange.requestHeaders.getFirst("X-AJA-Service-Token")
            val payload = exchange.requestBody.use { it.readAllBytes().decodeToString() }
            requests += payload
            val missing = payload.contains("missing-question") || payload.contains("withdrawn-question")
            val response = if (missing) """{"error":{"code":"NOT_FOUND","message":"Missing","requestId":"fixture"}}"""
                else """{"snapshots":[{"questionId":"q-core-process","revision":1,"contentHash":"${"a".repeat(64)}","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"correctOption":0,"explanation":"","image":null}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (missing) 404 else if (tokens.last() == "fixture-questions-core-token-1234567890") 200 else 403, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        var child: CoreChild? = null
        try {
            child = startCoreProcess("http://127.0.0.1:${server.address.port}/internal/v1/snapshots")
            val client = HttpClient.newHttpClient()
            val callsBeforeDeniedReference = tokens.size
            val denied = coreHttp(client, child, "POST", "/topics", outsiderCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Hidden reference","body":"Body","tags":[],"target":{"type":"question","id":"missing-question"},"attachmentId":null}""",
                "topic-hidden-${UUID.randomUUID()}")
            assertEquals(404, denied.statusCode(), denied.body())
            assertEquals(callsBeforeDeniedReference, tokens.size)
            val missing = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Missing reference","body":"Body","tags":[],"target":{"type":"question","id":"missing-question"},"attachmentId":null}""",
                "topic-missing-${UUID.randomUUID()}")
            assertEquals(404, missing.statusCode(), missing.body())
            val withdrawn = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawn reference","body":"Body","tags":[],"target":{"type":"question","id":"withdrawn-question"},"attachmentId":null}""",
                "topic-withdrawn-${UUID.randomUUID()}")
            assertEquals(404, withdrawn.statusCode(), withdrawn.body())
            val created = coreHttp(client, child, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Question reference","body":"Body","tags":[],"target":{"type":"question","id":"q-core-process"},"attachmentId":null}""",
                "topic-question-${UUID.randomUUID()}")
            assertEquals(201, created.statusCode(), created.body())
            assertEquals("q-core-process", tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("target").get("id").asText())
            assertEquals(List(3) { "fixture-questions-core-token-1234567890" }, tokens)
            assertTrue(requests.all { it.contains("\"ids\":[\"missing-question\"]") || it.contains("\"ids\":[\"withdrawn-question\"]") || it.contains("\"ids\":[\"q-core-process\"]") })
        } finally {
            child?.let(::stopCoreProcess)
            server.stop(0)
        }
    }

    @Test
    fun coreProcessUsesRealQuestionsProcessForTopicReferencesAndStableReplay() {
        val (adminCookie, _) = adminSession()
        val (_, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outageCookie, outageStudentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        createB07Enrollment(classId, outageStudentId, teacherId)
        val jdbcUrl = "${postgres.jdbcUrl}&currentSchema=questions"
        val questions = startQuestionsProcess()
        var core: CoreChild? = null
        try {
            DriverManager.getConnection(jdbcUrl, "questions", "questions-test").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("""INSERT INTO questions.question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation)
                        VALUES ('b12-real-question',1,now(),'Math','Algebra','easy','AJA',2026,'2 + 2?',ARRAY['4','5'],0,'Because'),
                               ('b12-real-withdrawn',1,now(),'Math','Algebra','easy','AJA',2026,'Withdrawn?',ARRAY['A','B'],0,'Hidden'),
                               ('b12-revoke-question',1,now(),'Math','Algebra','easy','AJA',2026,'Revoke?',ARRAY['A','B'],0,'Hidden'),
                               ('b12-outage-question',1,now(),'Math','Algebra','easy','AJA',2026,'Outage?',ARRAY['A','B'],0,'Hidden')""")
                    statement.execute("UPDATE questions.question_bank SET withdrawn_at=now() WHERE id='b12-real-withdrawn'")
                }
            }
            core = startCoreProcess("http://127.0.0.1:${questions.port}/internal/v1/snapshots")
            val client = HttpClient.newHttpClient()
            val snapshotUri = URI("http://127.0.0.1:${questions.port}/internal/v1/snapshots")
            val badToken = client.send(HttpRequest.newBuilder(snapshotUri).header("Content-Type", "application/json")
                .header("X-AJA-Service-Token", "wrong-questions-token-1234567890").POST(HttpRequest.BodyPublishers.ofString("""{"ids":["b12-real-question"]}""")).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(403, badToken.statusCode(), badToken.body())
            val exactToken = client.send(HttpRequest.newBuilder(snapshotUri).header("Content-Type", "application/json")
                .header("X-AJA-Service-Token", "fixture-questions-core-token-1234567890").POST(HttpRequest.BodyPublishers.ofString("""{"ids":["b12-real-question"]}""")).build(), HttpResponse.BodyHandlers.ofString())
            assertEquals(200, exactToken.statusCode(), exactToken.body())
            val validKey = "topic-real-${UUID.randomUUID()}"
            val validBody = """{"classId":"$classId","subjectId":"$subjectId","title":"Real question ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-question"},"attachmentId":null}"""
            val valid = coreHttp(client, core, "POST", "/topics", studentCookie, validBody, validKey)
            assertEquals(201, valid.statusCode(), valid.body())
            val topicId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("id").asText()
            assertEquals("b12-real-question", tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.body()).get("target").get("id").asText())

            val unauthorized = coreHttp(client, core, "POST", "/topics", outsiderCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Unauthorized ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-question"},"attachmentId":null}""",
                "topic-real-denied-${UUID.randomUUID()}")
            assertEquals(404, unauthorized.statusCode(), unauthorized.body())

            val missing = coreHttp(client, core, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Missing ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-missing"},"attachmentId":null}""",
                "topic-real-missing-${UUID.randomUUID()}")
            assertEquals(404, missing.statusCode(), missing.body())
            val withdrawn = coreHttp(client, core, "POST", "/topics", studentCookie,
                """{"classId":"$classId","subjectId":"$subjectId","title":"Withdrawn ref","body":"Body","tags":[],"target":{"type":"question","id":"b12-real-withdrawn"},"attachmentId":null}""",
                "topic-real-withdrawn-${UUID.randomUUID()}")
            assertEquals(404, withdrawn.statusCode(), withdrawn.body())

            DriverManager.getConnection(jdbcUrl, "questions", "questions-test").use { connection ->
                connection.createStatement().use { it.execute("UPDATE questions.question_bank SET withdrawn_at=now() WHERE id='b12-real-question'") }
            }
            val replayAfterWithdrawal = coreHttp(client, core, "POST", "/topics", studentCookie, validBody, validKey)
            assertEquals(201, replayAfterWithdrawal.statusCode(), replayAfterWithdrawal.body())
            assertEquals(valid.body(), replayAfterWithdrawal.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.topics WHERE id=?", Int::class.java, topicId))

            val revokedKey = "topic-real-revoke-${UUID.randomUUID()}"
            val revokedBody = validBody.replace("Real question ref", "Revoked replay ref")
                .replace("b12-real-question", "b12-revoke-question")
            val revokedCreate = coreHttp(client, core, "POST", "/topics", studentCookie, revokedBody, revokedKey)
            assertEquals(201, revokedCreate.statusCode(), revokedCreate.body())
            jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(studentCookie))
            val revokedReplay = coreHttp(client, core, "POST", "/topics", studentCookie, revokedBody, revokedKey)
            assertEquals(401, revokedReplay.statusCode(), revokedReplay.body())

            val outageKey = "topic-real-outage-${UUID.randomUUID()}"
            val outageBody = validBody.replace("Real question ref", "Outage replay ref")
                .replace("b12-real-question", "b12-outage-question")
            val outageCreate = coreHttp(client, core, "POST", "/topics", outageCookie, outageBody, outageKey)
            assertEquals(201, outageCreate.statusCode(), outageCreate.body())
            stopQuestionsProcess(questions)
            val outageReplay = coreHttp(client, core, "POST", "/topics",
                // The authorized session and request remain identical while the provider is offline.
                outageCookie, outageBody, outageKey)
            assertEquals(201, outageReplay.statusCode(), outageReplay.body())
            assertEquals(outageCreate.body(), outageReplay.body())
        } finally {
            core?.let(::stopCoreProcess)
            stopQuestionsProcess(questions)
        }
    }

    @Test
    fun coreAndQuestionsProcessesResolvePracticeAgainstCurrentAttemptAndAssignment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val moduleResponse = mvc.post("/api/v1/modules") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "module-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"subjectId":"$subjectId","title":"B15 practice","description":""}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val assessmentModuleId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(moduleResponse.response.contentAsString).get("id").asText()
        val assessmentId = createB07Assessment(assessmentModuleId, kind = "quiz", allowLate = true, deadline = null)
        val questionId = "b15-practice-${UUID.randomUUID()}"
        jdbc.update("""INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot)
            VALUES(?,?,0,'author',1,NULL,?::jsonb)""", assessmentId, questionId,
            """{"id":"$questionId","origin":"author","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}""")
        val questionsDb = "${postgres.jdbcUrl}&currentSchema=questions"
        val assessmentQuestionCount = jdbc.queryForObject("SELECT count(*) FROM core.assessment_questions WHERE assessment_id=?", Int::class.java, assessmentId)
        val core = startCoreProcess()
        val questions = startQuestionsProcess("http://127.0.0.1:${core.port}/internal/v1/session")
        try {
            DriverManager.getConnection(questionsDb, "questions", "questions-test").use { connection ->
                connection.createStatement().use { it.execute("""INSERT INTO questions.question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation)
                    VALUES ('$questionId',1,now(),'Math','Algebra','easy','AJA',2026,'1 + 1?','{2,3}',0,'Because')""") }
            }
            val client = HttpClient.newHttpClient()
            fun questionRequest(method: String, path: String, body: String? = null) = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:${questions.port}/api/v1$path"))
                    .header("Cookie", "aja_session=$studentCookie")
                    .header("Content-Type", "application/json")
                    .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.ofString(),
            )
            val before = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(200, before.statusCode(), before.body())
            assertTrue(before.body().contains("\"correct\":0"))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=?", Int::class.java, studentId))
            assertEquals(assessmentQuestionCount, jdbc.queryForObject("SELECT count(*) FROM core.assessment_questions WHERE assessment_id=?", Int::class.java, assessmentId))
            val repeatedPractice = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(200, repeatedPractice.statusCode(), repeatedPractice.body())
            assertEquals(before.body(), repeatedPractice.body())
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=?", Int::class.java, studentId))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=? AND score IS NOT NULL", Int::class.java, studentId))
            assertEquals(assessmentQuestionCount, jdbc.queryForObject("SELECT count(*) FROM core.assessment_questions WHERE assessment_id=?", Int::class.java, assessmentId))

            val getList = questionRequest("GET", "/questions")
            assertEquals(200, getList.statusCode(), getList.body())
            assertTrue(getList.body().contains(questionId))
            val getDetail = questionRequest("GET", "/questions/$questionId")
            assertEquals(200, getDetail.statusCode(), getDetail.body())

            fun racePractice(mutate: (java.sql.Connection) -> Unit): HttpResponse<String> {
                val executor = Executors.newSingleThreadExecutor()
                try {
                    return DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                        connection.autoCommit = false
                        connection.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(812734)") }
                        val response = executor.submit<HttpResponse<String>> {
                            questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
                        }
                        awaitLockWait("pg_advisory_xact_lock")
                        mutate(connection)
                        connection.commit()
                        response.get(10, TimeUnit.SECONDS)
                    }
                } finally {
                    executor.shutdownNow()
                }
            }

            val pendingSnapshot = """[{"id":"$questionId","origin":"author","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}]"""
            val pendingOrder = """["$questionId"]"""
            val barrierAttemptId = UUID.randomUUID().toString()
            val attemptStartedBeforeDecision = racePractice { connection ->
                connection.prepareStatement("""INSERT INTO core.attempts(id,assessment_id,user_id,revision,created_at,updated_at,updated_by,started_at,answers,question_order,questions,questions_updated_at)
                    VALUES(?,?,?,1,now(),now(),?,now(),'{}'::jsonb,?::jsonb,?::jsonb,now())""").use { statement ->
                    statement.setString(1, barrierAttemptId)
                    statement.setString(2, assessmentId)
                    statement.setString(3, studentId)
                    statement.setString(4, studentId)
                    statement.setString(5, pendingOrder)
                    statement.setString(6, pendingSnapshot)
                    statement.executeUpdate()
                }
            }
            assertEquals(409, attemptStartedBeforeDecision.statusCode(), attemptStartedBeforeDecision.body())
            jdbc.update("DELETE FROM core.attempts WHERE id=?", barrierAttemptId)

            val started = coreHttp(client, core, "POST", "/attempts", studentCookie, """{"assessmentId":"$assessmentId"}""", "b15-${UUID.randomUUID()}")
            assertEquals(201, started.statusCode(), started.body())
            val attemptsBeforeBlockedPractice = jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=?", Int::class.java, studentId)
            val blocked = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(409, blocked.statusCode(), blocked.body())
            assertFalse(blocked.body().contains("\"correct\""))
            assertFalse(blocked.body().contains("Because"))
            assertEquals(attemptsBeforeBlockedPractice, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=?", Int::class.java, studentId))

            jdbc.update("DELETE FROM core.attempts WHERE user_id=?", studentId)
            val logout = racePractice { connection ->
                connection.prepareStatement("UPDATE core.sessions SET revoked_at=now() WHERE token_hash=?").use {
                    it.setString(1, sha256(studentCookie)); it.executeUpdate()
                }
            }
            assertEquals(401, logout.statusCode(), logout.body())
            jdbc.update("UPDATE core.sessions SET revoked_at=NULL WHERE token_hash=?", sha256(studentCookie))
            val deactivated = racePractice { connection ->
                connection.prepareStatement("UPDATE core.users SET active=false WHERE id=?").use {
                    it.setString(1, studentId); it.executeUpdate()
                }
            }
            assertEquals(401, deactivated.statusCode(), deactivated.body())
            jdbc.update("UPDATE core.users SET active=true WHERE id=?", studentId)
            val assignedAttempt = coreHttp(client, core, "POST", "/attempts", studentCookie,
                """{"assessmentId":"$assessmentId"}""", "b15-enrollment-${UUID.randomUUID()}")
            assertEquals(201, assignedAttempt.statusCode(), assignedAttempt.body())
            val beforeEnrollmentEnd = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(409, beforeEnrollmentEnd.statusCode(), beforeEnrollmentEnd.body())
            val enrollmentEnded = racePractice { connection ->
                connection.prepareStatement("UPDATE core.enrollments SET status='ended' WHERE student_id=? AND class_id=?").use {
                    it.setString(1, studentId); it.setString(2, classId); it.executeUpdate()
                }
            }
            assertEquals(200, enrollmentEnded.statusCode(), enrollmentEnded.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=? AND submitted_at IS NULL", Int::class.java, studentId))
            jdbc.update("UPDATE core.enrollments SET status='active' WHERE student_id=? AND class_id=?", studentId, classId)
            val beforeUnassignment = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(409, beforeUnassignment.statusCode(), beforeUnassignment.body())
            val unassigned = racePractice { connection ->
                connection.prepareStatement("UPDATE core.enrollments SET unassigned_assessment_ids=jsonb_build_array(?) WHERE student_id=? AND class_id=?").use {
                    it.setString(1, assessmentId); it.setString(2, studentId); it.setString(3, classId); it.executeUpdate()
                }
            }
            assertEquals(200, unassigned.statusCode(), unassigned.body())
            assertTrue(unassigned.body().contains("\"correct\":0"))
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=? AND submitted_at IS NULL AND score IS NULL", Int::class.java, studentId))
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=? AND question_order @> jsonb_build_array(?) AND questions @> jsonb_build_array(jsonb_build_object('id', ?))", Int::class.java, studentId, questionId, questionId))
            val repeatedAfterUnassignment = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(200, repeatedAfterUnassignment.statusCode(), repeatedAfterUnassignment.body())
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE user_id=? AND submitted_at IS NULL AND score IS NULL", Int::class.java, studentId))

            DriverManager.getConnection(questionsDb, "questions", "questions-test").use { connection ->
                connection.createStatement().use { it.execute("UPDATE questions.question_bank SET withdrawn_at=now() WHERE id='$questionId'") }
            }
            val withdrawn = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(404, withdrawn.statusCode(), withdrawn.body())
            DriverManager.getConnection(questionsDb, "questions", "questions-test").use { connection ->
                connection.createStatement().use { it.execute("UPDATE questions.question_bank SET withdrawn_at=NULL WHERE id='$questionId'") }
            }
            stopCoreProcess(core)
            val unavailable = questionRequest("POST", "/questions/$questionId/practice", """{"choice":0}""")
            assertEquals(503, unavailable.statusCode(), unavailable.body())
            assertFalse(unavailable.body().contains("\"correct\""))
            assertFalse(unavailable.body().contains("Because"))
        } finally {
            stopQuestionsProcess(questions)
            stopCoreProcess(core)
        }
    }

    @Test
    fun announcementCreateReplayRevalidatesTeacherAssignment() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val key = "announcement-${UUID.randomUUID()}"
        val body = """{"classId":"$classId","subjectId":"$subjectId","title":"Scoped notice","body":"Body","notify":false,"occurrence":null}"""

        val created = liveHttp("POST", "/announcements", teacherCookie, body, key)
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val unassigned = liveHttp("PATCH", "/subjects/$subjectId", adminCookie, """{"expectedRevision":1,"teacherIds":[]}""")
        assertEquals(200, unassigned.statusCode(), unassigned.body())
        assertEquals(404, liveHttp("GET", "/announcements/$id", teacherCookie).statusCode())

        val replay = liveHttp("POST", "/announcements", teacherCookie, body, key)
        assertEquals(403, replay.statusCode(), replay.body())
    }

    @Test
    fun subjectClassTransferMovesAnnouncementVisibilityWithoutResendingNotification() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (oldStudentCookie, oldStudentId) = userSession("student")
        val (newStudentCookie, newStudentId) = userSession("student")
        val oldClassId = createB06Class(adminCookie)
        val newClassId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, oldClassId, teacherId)
        createB07Enrollment(oldClassId, oldStudentId, teacherId)
        createB07Enrollment(newClassId, newStudentId, teacherId)
        val created = liveHttp("POST", "/announcements", teacherCookie,
            """{"classId":"$oldClassId","subjectId":"$subjectId","title":"Subject notice","body":"Body","notify":true,"occurrence":null}""",
            "announcement-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=?", Int::class.java, id))
        val eventsBeforeTransfer = jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java)!!

        val transferred = liveHttp("PATCH", "/subjects/$subjectId", adminCookie, """{"expectedRevision":1,"classId":"$newClassId"}""")
        assertEquals(200, transferred.statusCode(), transferred.body())
        assertEquals(404, liveHttp("GET", "/announcements/$id", oldStudentCookie).statusCode())
        assertEquals(200, liveHttp("GET", "/announcements/$id", newStudentCookie).statusCode())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND recipient_id=?", Int::class.java, id, oldStudentId))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients WHERE announcement_id=? AND recipient_id=?", Int::class.java, id, newStudentId))
        assertEquals(eventsBeforeTransfer, jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java))
    }

    @Test
    fun announcementPatchWithdrawalAndInvalidDatesPreserveTheContract() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val beforeEvents = jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java)!!
        val created = liveHttp("POST", "/announcements", teacherCookie,
            """{"classId":"$classId","subjectId":"$subjectId","title":"Draft","body":"","notify":false,"occurrence":{"date":"2026-11-12","time":null,"allDay":true}}""",
            "announcement-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        assertEquals(beforeEvents, jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java))

        val patchResult = liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":1,"title":"Edited","occurrence":null}""")
        assertEquals(200, patchResult.statusCode(), patchResult.body())
        val patched = tools.jackson.databind.json.JsonMapper.builder().build().readTree(patchResult.body())
        assertEquals(2, patched.get("revision").intValue())
        assertEquals("Edited", patched.get("title").asString())
        assertTrue(patched.get("occurrence").isNull)
        val metadataPatch = liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":2,"body":"Metadata only"}""")
        assertEquals(200, metadataPatch.statusCode(), metadataPatch.body())
        val metadata = tools.jackson.databind.json.JsonMapper.builder().build().readTree(metadataPatch.body())
        assertEquals(3, metadata.get("revision").intValue())
        assertTrue(metadata.get("occurrence").isNull)
        val withdrawn = liveHttp("POST", "/announcements/$id/withdraw", teacherCookie, """{"expectedRevision":3}""")
        assertEquals(200, withdrawn.statusCode(), withdrawn.body())
        assertEquals(4, tools.jackson.databind.json.JsonMapper.builder().build().readTree(withdrawn.body()).get("revision").intValue())
        assertEquals(404, liveHttp("GET", "/announcements/$id", studentCookie).statusCode())
        assertEquals(beforeEvents, jdbc.queryForObject("SELECT count(*) FROM core.outbox_events WHERE event_type='academic.announcement-notification-requested'", Int::class.java))
        val invalid = liveHttp("POST", "/announcements", teacherCookie,
            """{"classId":"$classId","subjectId":null,"title":"Invalid date","body":"","notify":false,"occurrence":{"date":"2026-02-30","time":null,"allDay":true}}""",
            "invalid-${UUID.randomUUID()}")
        assertEquals(422, invalid.statusCode(), invalid.body())
    }

    @Test
    fun calendarShowsOnlyVisibleDeadlinesInBrasiliaTime() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (outsiderCookie, _) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val deadline = mutableClock.instant().minusSeconds(600).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        createB07Enrollment(classId, studentId, teacherId, enrolledAt = deadline.plusSeconds(1))
        val assessmentId = createB07Assessment(moduleId, deadline = deadline)
        assertEquals(200, liveHttp("GET", "/assessments/$assessmentId", studentCookie).statusCode())
        val localDeadline = deadline.atZone(ZoneId.of("America/Sao_Paulo"))
        val date = localDeadline.toLocalDate().toString()
        val time = localDeadline.toLocalTime().toString().substring(0, 5)
        val calendar = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/calendar?from=$date&to=$date", studentCookie).body())
        assertEquals(1, calendar.get("total").intValue())
        assertEquals("assessment", calendar.get("items").get(0).get("source").asText())
        assertEquals(date, calendar.get("items").get(0).get("date").asText())
        assertEquals(time, calendar.get("items").get(0).get("time").asText())
        val hidden = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/calendar?from=$date&to=$date", outsiderCookie).body())
        assertEquals(0, hidden.get("total").intValue())
        assertEquals(422, liveHttp("GET", "/calendar?from=2026-13-12", studentCookie).statusCode())
    }

    @Test
    fun failedAnnouncementOutboxInsertRollsBackTheAnnouncement() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        createB06Subject(adminCookie, classId, teacherId)
        val (_, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val before = jdbc.queryForObject("SELECT count(*) FROM core.announcements", Int::class.java)!!
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create or replace function core.reject_announcement_outbox() returns trigger language plpgsql as \$\$ begin if new.event_type='academic.announcement-notification-requested' then raise exception 'forced announcement outbox failure'; end if; return new; end \$\$")
                statement.execute("create trigger reject_announcement_outbox before insert on core.outbox_events for each row execute function core.reject_announcement_outbox()")
            }
        }
        try {
            val response = liveHttp("POST", "/announcements", teacherCookie,
                """{"classId":"$classId","subjectId":null,"title":"Rollback","body":"No orphan","notify":true,"occurrence":null}""",
                "rollback-${UUID.randomUUID()}")
            assertEquals(500, response.statusCode(), response.body())
            assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM core.announcements", Int::class.java))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists reject_announcement_outbox on core.outbox_events")
                    statement.execute("drop function if exists core.reject_announcement_outbox()")
                }
            }
        }
    }

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
    fun assessmentCanBeCreatedReadAndPublishedByItsAssignedTeacher() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val createKey = "assessment-${UUID.randomUUID()}"
        val createBody = """{"moduleId":"$moduleId","title":"B08 work","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
        val created = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", createKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = createBody
        }.andExpect { status { isCreated() } }.andReturn()
        val body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString)
        val id = body.get("id").asText()
        assertEquals("draft", body.get("status").asText())
        assertFalse(body.get("hasSubmissions").asBoolean())
        mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", createKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = createBody
        }.andExpect { status { isCreated() }; jsonPath("$.id") { value(id) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.assessments WHERE id=?", Int::class.java, id))

        mvc.get("/api/v1/assessments/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isNotFound() } }

        mvc.get("/api/v1/assessments/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.id") { value(id) }; jsonPath("$.status") { value("draft") } }

        val publishKey = "publish-${UUID.randomUUID()}"
        val publishBody = """{"expectedRevision":1}"""
        mvc.post("/api/v1/assessments/$id/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", publishKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = publishBody
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("published") }; jsonPath("$.publishedAt") { exists() } }
        mvc.post("/api/v1/assessments/$id/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", publishKey)
            cookie(Cookie("aja_session", teacherCookie))
            content = publishBody
        }.andExpect { status { isOk() }; jsonPath("$.status") { value("published") } }
        mvc.get("/api/v1/assessments/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.assigned") { value(true) }; jsonPath("$.questions") { doesNotExist() } }
        val (unassignedTeacherCookie, _) = userSession("teacher")
        mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "unassigned-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", unassignedTeacherCookie))
            content = createBody
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun attemptCanBeStartedAndResumedWithPersistedAnswers() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val question = """{"source":"authored","clientKey":"q","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}}"""
        val created = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","title":"Attempt quiz","description":"","kind":"quiz","maxGrade":10,"deadline":null,"required":true,"allowLate":false,"questions":[$question]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val assessmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/assessments/$assessmentId/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }

        val startKey = "start-${UUID.randomUUID()}"
        val started = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", startKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val attemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(started.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "resume-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.id") { value(attemptId) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE assessment_id=? AND user_id=?", Int::class.java, assessmentId, studentId))
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1,"answers":{"${jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, assessmentId)}":0}}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(2) } }
        val questionId = jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, assessmentId)
        val revisedQuestion = """{"source":"authored","clientKey":"q","questionId":"$questionId","question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"Changed after start","options":["3","2"],"image":null,"correct":1,"explanation":"Changed"}}"""
        mvc.patch("/api/v1/assessments/$assessmentId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "edit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"questions":[$revisedQuestion]}"""
        }.andExpect { status { isConflict() } }
        mvc.get("/api/v1/attempts/$attemptId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.answers.*") { value(0) }; jsonPath("$.questions[0].statement") { value("1 + 1?") }; jsonPath("$.questions[0].options[0]") { value("2") }; jsonPath("$.questions[0].correct") { doesNotExist() }; jsonPath("$.questions[0].explanation") { doesNotExist() } }
        mvc.patch("/api/v1/assessments/$assessmentId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "confirmed-edit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"confirmResetDrafts":true,"questions":[$revisedQuestion]}"""
        }.andExpect { status { isOk() } }
        mvc.get("/api/v1/attempts/$attemptId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isNotFound() } }
        val restartedDraft = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", startKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val replacementAttemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(restartedDraft.response.contentAsString).get("id").asText()
        assertTrue(replacementAttemptId != attemptId)
        mvc.get("/api/v1/attempts/$replacementAttemptId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.answers") { isEmpty() }; jsonPath("$.questions[0].statement") { value("Changed after start") } }
    }

    @Test
    fun concurrentSubmitKeepsOneReceiptAndResultForTwentyFiveHours() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val (outsiderCookie, _) = userSession("student")
        val question = """{"source":"authored","clientKey":"q","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}}"""
        val created = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","title":"Submit quiz","description":"","kind":"quiz","maxGrade":10,"deadline":null,"required":true,"allowLate":false,"questions":[$question]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val assessment = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString)
        val assessmentId = assessment.get("id").asText()
        val questionId = assessment.get("questionIds").get(0).asText()
        mvc.post("/api/v1/assessments/$assessmentId/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        val started = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "start-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val attemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(started.response.contentAsString).get("id").asText()
        mvc.get("/api/v1/attempts/$attemptId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isNotFound() } }
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
            content = """{"expectedRevision":1,"answers":{}}"""
        }.andExpect { status { isNotFound() } }
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "outsider-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", outsiderCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isNotFound() } }
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1,"answers":{"$questionId":0}}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(2) } }

        val submitKey = "submit-${UUID.randomUUID()}"
        val body = """{"expectedRevision":2}"""
        val gate = java.util.concurrent.CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val requests = (1..2).map {
            pool.submit<MvcResult> {
                gate.await()
                mvc.post("/api/v1/attempts/$attemptId/submit") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", submitKey)
                    cookie(Cookie("aja_session", studentCookie))
                    content = body
                }.andReturn()
            }
        }
        gate.countDown()
        val responses = requests.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdownNow()
        assertEquals(listOf(200, 200), responses.map { it.response.status }.sorted())
        val result = tools.jackson.databind.json.JsonMapper.builder().build().readTree(responses.first().response.contentAsString)
        val receiptId = result.get("receipt").get("id").asText()
        assertEquals(receiptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(responses.last().response.contentAsString).get("receipt").get("id").asText())
        assertEquals(10.0, result.get("score").doubleValue())
        assertEquals("1 + 1?", result.get("questions").get(0).get("statement").asText())
        assertEquals(0, result.get("questions").get(0).get("correct").intValue())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE id=? AND receipt_id=?", Int::class.java, attemptId, receiptId))
        val changedAfterSubmit = """{"source":"authored","clientKey":"q","questionId":"$questionId","question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"Changed after submission","options":["3","2"],"image":null,"correct":1,"explanation":"Changed"}}"""
        mvc.patch("/api/v1/assessments/$assessmentId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "edit-submitted-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"confirmResetDrafts":true,"questions":[$changedAfterSubmit]}"""
        }.andExpect { status { isConflict() } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE id=? AND submitted_at IS NOT NULL AND receipt_id=?", Int::class.java, attemptId, receiptId))
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", submitKey)
            cookie(Cookie("aja_session", studentCookie))
            content = body
        }.andExpect { status { isOk() }; jsonPath("$.receipt.id") { value(receiptId) } }
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", submitKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("IDEMPOTENCY_CONFLICT") } }
        val committedKey = "committed-${UUID.randomUUID()}"
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", committedKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":3}"""
        }.andExpect { status { isOk() }; jsonPath("$.receipt.id") { value(receiptId) } }
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", committedKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() }; jsonPath("$.error.code") { value("IDEMPOTENCY_CONFLICT") } }
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":3,"answers":{}}"""
        }.andExpect { status { isConflict() } }
        mvc.get("/api/v1/attempts/$attemptId/result") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", outsiderCookie))
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/attempts?assessmentId=$assessmentId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].id") { value(attemptId) }; jsonPath("$.items[0].studentName") { exists() } }
        mvc.get("/api/v1/attempts/$attemptId/result") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.questions[0].correct") { value(0) } }
        val (unassignedTeacherCookie, _) = userSession("teacher")
        mvc.get("/api/v1/attempts/$attemptId/result") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", unassignedTeacherCookie))
        }.andExpect { status { isNotFound() } }

        jdbc.update("UPDATE core.sessions SET expires_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant().plus(Duration.ofHours(30))), sha256(studentCookie))
        mutableClock.advance(Duration.ofHours(25))
        val webServer = checkNotNull(webContext.webServer)
    }

    @Test
    fun attemptDeadlineIsExclusiveInBrasiliaAndLatePolicyIsApplied() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        val oldEnrollmentTime = mutableClock.instant().minusSeconds(600)
        createB07Enrollment(classId, studentId, teacherId, enrolledAt = oldEnrollmentTime)
        val now = mutableClock.instant()
        val expired = createAttemptQuiz(moduleId, now.plusSeconds(60), false)
        val started = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "start-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$expired"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val attemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(started.response.contentAsString).get("id").asText()
        mutableClock.advance(Duration.ofSeconds(60))
        val questionId = jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, expired)
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1,"answers":{"$questionId":0}}"""
        }.andExpect { status { isConflict() } }
        mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() } }

        val lateAccepted = createAttemptQuiz(moduleId, mutableClock.instant().minusSeconds(1), true)
        mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "late-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$lateAccepted"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun attemptResumeAndReceiptSurviveFreshCoreProcesses() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createAttemptQuiz(moduleId, mutableClock.instant().plusSeconds(600), false)
        val start = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "process-start-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val attemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(start.response.contentAsString).get("id").asText()
        val questionId = jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, assessmentId)
        val firstClient = HttpClient.newHttpClient()
        val secondClient = HttpClient.newHttpClient()
        var process: CoreChild? = null
        try {
            var child = startCoreProcess()
            process = child
            val initialResume = coreHttp(firstClient, child, "GET", "/attempts/$attemptId", studentCookie)
            assertEquals(200, initialResume.statusCode(), initialResume.body())
            assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(initialResume.body()).get("revision").intValue())
            stopCoreProcess(child)
            process = null

            child = startCoreProcess()
            process = child
            val resumedByFirst = coreHttp(firstClient, child, "GET", "/attempts/$attemptId", studentCookie)
            val resumedBySecond = coreHttp(secondClient, child, "GET", "/attempts/$attemptId", studentCookie)
            assertEquals(200, resumedByFirst.statusCode(), resumedByFirst.body())
            assertEquals(200, resumedBySecond.statusCode(), resumedBySecond.body())
            assertEquals(attemptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(resumedByFirst.body()).get("id").asText())
            assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(resumedBySecond.body()).get("revision").intValue())
            val answer = coreHttp(firstClient, child, "PATCH", "/attempts/$attemptId/answers", studentCookie,
                """{"expectedRevision":1,"answers":{"$questionId":0}}""")
            assertEquals(200, answer.statusCode(), answer.body())
            val answerSeenBySecond = coreHttp(secondClient, child, "GET", "/attempts/$attemptId", studentCookie)
            assertEquals(2, tools.jackson.databind.json.JsonMapper.builder().build().readTree(answerSeenBySecond.body()).get("revision").intValue())
            stopCoreProcess(child)
            process = null

            child = startCoreProcess()
            process = child
            val resumedAgain = coreHttp(secondClient, child, "GET", "/attempts/$attemptId", studentCookie)
            assertEquals(200, resumedAgain.statusCode(), resumedAgain.body())
            assertEquals(0, tools.jackson.databind.json.JsonMapper.builder().build().readTree(resumedAgain.body()).get("answers").get(questionId).intValue())
            val submitKey = "process-submit-${UUID.randomUUID()}"
            val submitBody = """{"expectedRevision":2}"""
            val submitted = coreHttp(firstClient, child, "POST", "/attempts/$attemptId/submit", studentCookie, submitBody, submitKey)
            assertEquals(200, submitted.statusCode(), submitted.body())
            val receiptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(submitted.body()).get("receipt").get("id").asText()
            stopCoreProcess(child)
            process = null

            jdbc.update("UPDATE core.command_idempotency SET created_at=created_at-interval '25 hours',expires_at=expires_at-interval '25 hours' WHERE actor_id=? AND operation='submitAttempt' AND target=? AND idempotency_key=?", studentId, "/attempts/$attemptId/submit", submitKey)
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.command_idempotency WHERE actor_id=? AND operation='submitAttempt' AND idempotency_key=? AND expires_at>clock_timestamp()", Int::class.java, studentId, submitKey))

            child = startCoreProcess()
            process = child
            val replayed = coreHttp(secondClient, child, "POST", "/attempts/$attemptId/submit", studentCookie, submitBody, submitKey)
            assertEquals(200, replayed.statusCode(), replayed.body())
            assertEquals(receiptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(replayed.body()).get("receipt").get("id").asText())
            val result = coreHttp(firstClient, child, "GET", "/attempts/$attemptId/result", studentCookie)
            assertEquals(200, result.statusCode(), result.body())
            assertEquals(receiptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.body()).get("receipt").get("id").asText())
        } finally {
            process?.let(::stopCoreProcess)
        }
    }

    @Test
    fun attemptLifecycleWorksOverTheRunningCoreHttpServer() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val question = """{"source":"authored","clientKey":"q","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"HTTP quiz","options":["right","wrong"],"image":null,"correct":0,"explanation":"correct"}}"""
        val created = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","title":"HTTP quiz","description":"","kind":"quiz","maxGrade":2,"deadline":null,"required":true,"allowLate":false,"questions":[$question]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val assessment = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString)
        val assessmentId = assessment.get("id").asText()
        val questionId = assessment.get("questionIds").get(0).asText()
        mvc.post("/api/v1/assessments/$assessmentId/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }

        val client = HttpClient.newHttpClient()
        fun request(method: String, path: String, body: String? = null, key: String? = null): HttpResponse<String> {
            val builder = HttpRequest.newBuilder(URI("http://localhost:${checkNotNull(webContext.webServer).port}/api/v1$path"))
                .header("Origin", "https://aja.example.test")
                .header("Cookie", "aja_session=$studentCookie")
                .header("Content-Type", "application/json")
            key?.let { builder.header("Idempotency-Key", it) }
            val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
            return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        }
        val started = request("POST", "/attempts", """{"assessmentId":"$assessmentId"}""", "http-start-${UUID.randomUUID()}")
        assertEquals(201, started.statusCode())
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val attemptId = mapper.readTree(started.body()).get("id").asText()
        val saved = request("PATCH", "/attempts/$attemptId/answers", """{"expectedRevision":1,"answers":{"$questionId":0}}""")
        assertEquals(200, saved.statusCode())
        assertEquals(2, mapper.readTree(saved.body()).get("revision").intValue())
        val submitted = request("POST", "/attempts/$attemptId/submit", """{"expectedRevision":2}""", "http-submit-${UUID.randomUUID()}")
        assertEquals(200, submitted.statusCode())
        assertEquals(2.0, mapper.readTree(submitted.body()).get("score").doubleValue())
        val result = request("GET", "/attempts/$attemptId/result")
        assertEquals(200, result.statusCode())
        assertEquals("correct", mapper.readTree(result.body()).get("questions").get(0).get("explanation").asText())
    }

    @Test
    fun assessmentKindChangesMustPreserveCompositionInvariant() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val quiz = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","title":"Kind invariant","description":"","kind":"quiz","maxGrade":5,"deadline":null,"required":true,"allowLate":false,"questions":[{"source":"authored","clientKey":"q","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":""}}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val quizId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(quiz.response.contentAsString).get("id").asText()
        mvc.patch("/api/v1/assessments/$quizId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-to-work-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1,"kind":"work"}"""
        }.andExpect { status { isConflict() } }

        val work = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "work-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"moduleId":"$moduleId","title":"Empty work","description":"","kind":"work","maxGrade":null,"deadline":null,"required":true,"allowLate":false,"questions":[]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val workId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(work.response.contentAsString).get("id").asText()
        mvc.patch("/api/v1/assessments/$workId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "work-to-quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1,"kind":"quiz"}"""
        }.andExpect { status { isConflict() } }
        jdbc.update("UPDATE core.assessments SET kind='work' WHERE id=?", quizId)
        mvc.post("/api/v1/assessments/$quizId/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "invalid-work-publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun assessmentRejectsMalformedDeadlineAndOverflowingAuthoredIntegers() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val base = """{"moduleId":"$moduleId","title":"Bad input","description":"","kind":"quiz","maxGrade":5,"deadline":"not-an-instant","required":true,"allowLate":false,"questions":[{"source":"authored","clientKey":"q","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":""}}]}"""
        mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "bad-deadline-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = base
        }.andExpect { status { isUnprocessableEntity() } }
        val valid = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "valid-deadline-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = base.replace("\"deadline\":\"not-an-instant\"", "\"deadline\":null")
        }.andExpect { status { isCreated() } }.andReturn()
        val assessmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(valid.response.contentAsString).get("id").asText()
        mvc.patch("/api/v1/assessments/$assessmentId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "patch-bad-deadline-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1,"deadline":"not-an-instant"}"""
        }.andExpect { status { isUnprocessableEntity() } }
        for ((field, value) in listOf("year" to "4294967296", "correct" to "4294967296")) {
            val input = base.replace("\"deadline\":\"not-an-instant\"", "\"deadline\":null")
                .replace(if (field == "year") "\"year\":2026" else "\"correct\":0", "\"$field\":$value")
            mvc.post("/api/v1/assessments") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                header("Idempotency-Key", "overflow-$field-${UUID.randomUUID()}")
                cookie(Cookie("aja_session", teacherCookie))
                content = input
            }.andExpect { status { isUnprocessableEntity() } }
        }
    }

    @Test
    fun assessmentSnapshotsAreCapturedOnceAndHiddenFromStudents() {
        questionSnapshotProvider.requested.clear()
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (secondTeacherCookie, secondTeacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) SELECT subject_id,? FROM core.modules WHERE id=?", secondTeacherId, moduleId)
        createB07Enrollment(classId, studentId, teacherId)
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val questionImageId = "question-image-${UUID.randomUUID()}"
        questionSnapshotProvider.imageFileId = questionImageId
        jdbc.update("INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at) VALUES(?,'question',?,'figure.png','image/png',?,?,'attached',?,?)", questionImageId, "bank-1", imageBytes.size, "b".repeat(64), imageBytes, java.sql.Timestamp.from(mutableClock.instant()))
        val quiz = """{"moduleId":"$moduleId","title":"Snapshot quiz","description":"Instructions","kind":"quiz","maxGrade":10,"deadline":null,"required":true,"allowLate":false,"questions":[{"source":"bank","questionId":"bank-1"},{"source":"authored","clientKey":"author-1","questionId":null,"question":{"subject":"Math","topic":"Algebra","difficulty":"medium","institution":"AJA","year":2026,"statement":"Authored","options":["a","b"],"image":null,"correct":0,"explanation":"Because"}}]}"""
        val before = jdbc.queryForObject("SELECT count(*) FROM core.assessments", Int::class.java)
        mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "offline-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = quiz.replace("bank-1", "offline")
        }.andExpect { status { isServiceUnavailable() } }
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM core.assessments", Int::class.java))

        val duplicate = quiz.replace("\"questionId\":\"bank-1\"},{\"source\":\"authored\"", "\"questionId\":\"bank-1\"},{\"source\":\"bank\",\"questionId\":\"bank-1\"},{\"source\":\"authored\"")
        mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "duplicate-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = duplicate
        }.andExpect { status { isUnprocessableEntity() } }
        assertEquals(1, questionSnapshotProvider.requested.size)

        val created = mvc.post("/api/v1/assessments") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "quiz-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = quiz
        }.andExpect { status { isCreated() }; jsonPath("$.questions[0].statement") { value("Original bank-1") }; jsonPath("$.questions[1].origin") { value("author") } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asText()
        val authoredId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("questions").get(1).get("id").asText()
        assertEquals(listOf("bank-1"), questionSnapshotProvider.requested.last())
        assertEquals("Original bank-1", jdbc.queryForObject("SELECT snapshot->>'statement' FROM core.assessment_questions WHERE assessment_id=? AND question_id='bank-1'", String::class.java, id))
        assertEquals(3, jdbc.queryForObject("SELECT source_revision FROM core.assessment_questions WHERE assessment_id=? AND question_id='bank-1'", Int::class.java, id))
        val copiedImageId = jdbc.queryForObject("SELECT substring(snapshot->'image'->>'src' from '/files/([^/]+)/content$') FROM core.assessment_questions WHERE assessment_id=? AND question_id='bank-1'", String::class.java, id)!!
        assertTrue(copiedImageId != questionImageId)
        assertEquals("assessment", jdbc.queryForObject("SELECT purpose FROM core.files WHERE id=?", String::class.java, copiedImageId))
        assertEquals(imageBytes.toList(), jdbc.queryForObject("SELECT content FROM core.files WHERE id=?", ByteArray::class.java, copiedImageId)?.toList())
        mvc.get("/api/v1/files/$copiedImageId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.purpose") { value("question") } }
        mvc.get("/api/v1/files/$copiedImageId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isNotFound() } }

        mvc.post("/api/v1/assessments/$id/publish") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.get("/api/v1/files/$copiedImageId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; content { bytes(imageBytes) } }
        mvc.get("/api/v1/assessments/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.questionCount") { value(2) }; jsonPath("$.questions") { doesNotExist() }; jsonPath("$.correct") { doesNotExist() }; jsonPath("$.explanation") { doesNotExist() } }
        mvc.get("/api/v1/assessments?moduleId=$moduleId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.items[0].questionCount") { value(2) }; jsonPath("$.items[0].questions") { doesNotExist() } }

        mvc.patch("/api/v1/assessments/$id") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "metadata-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"title":"Updated title"}"""
        }.andExpect { status { isOk() }; jsonPath("$.title") { value("Updated title") } }
        assertEquals(2, questionSnapshotProvider.requested.size)
        assertEquals("Original bank-1", jdbc.queryForObject("SELECT snapshot->>'statement' FROM core.assessment_questions WHERE assessment_id=? AND question_id='bank-1'", String::class.java, id))

        val newComposition = """[{"source":"bank","questionId":"bank-1"},{"source":"authored","clientKey":"author-1","questionId":"$authoredId","question":{"subject":"Math","topic":"Algebra","difficulty":"medium","institution":"AJA","year":2026,"statement":"Authored","options":["a","b"],"image":null,"correct":0,"explanation":"Because"}},{"source":"bank","questionId":"bank-2"}]"""
        mvc.patch("/api/v1/assessments/$id") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "unconfirmed-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3,"questions":$newComposition}"""
        }.andExpect { status { isConflict() } }
        assertEquals(2, questionSnapshotProvider.requested.size)

        mvc.patch("/api/v1/assessments/$id") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "confirmed-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3,"confirmResetDrafts":true,"questions":$newComposition}"""
        }.andExpect { status { isOk() }; jsonPath("$.questionIds.length()") { value(3) } }
        assertEquals(listOf("bank-2"), questionSnapshotProvider.requested.last())
        assertEquals(copiedImageId, jdbc.queryForObject("SELECT substring(snapshot->'image'->>'src' from '/files/([^/]+)/content$') FROM core.assessment_questions WHERE assessment_id=? AND question_id='bank-1'", String::class.java, id))
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
    fun submissionDraftCanBeCreatedForAnAssignedPublishedWork() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (otherTeacherCookie, otherTeacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (peerCookie, _) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) SELECT subject_id,? FROM core.modules WHERE id=?", otherTeacherId, moduleId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        jdbc.update("UPDATE core.assessments SET max_grade=10 WHERE id=?", assessmentId)
        val fileBytes = "student work".toByteArray()
        val upload = mvc.perform(multipart("/api/v1/files").file(MockMultipartFile("file", "work.txt", "text/plain", fileBytes))
            .param("purpose", "work").param("parentId", assessmentId).cookie(Cookie("aja_session", studentCookie))
            .header("Origin", "https://aja.example.test").header("Idempotency-Key", "upload-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(upload.response.contentAsString).get("id").asString()
        val otherAssessmentId = createB07Assessment(moduleId)
        mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "wrong-parent-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$otherAssessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"work.txt"}]}"""
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE assessment_id=?", Int::class.java, otherAssessmentId))

        fun post(cookie: String, key: String) = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", cookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"work.txt"},{"kind":"link","name":"Research","url":"https://example.test/work"}]}"""
        }.andReturn()

        val createKey = "submission-${UUID.randomUUID()}"
        val created = post(studentCookie, createKey)
        val parsed = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString)
        val submissionId = parsed.get("id").asString()
        assertEquals(201, created.response.status)
        assertEquals(assessmentId, parsed.get("assessmentId").asString())
        assertTrue(parsed.get("submittedAt").isNull)
        assertEquals(submissionId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(post(studentCookie, createKey).response.contentAsString).get("id").asString())

        val submitKey = "submit-${UUID.randomUUID()}"
        val submitBody = """{"expectedRevision":1}"""
        fun submit(key: String) = mvc.post("/api/v1/submissions/$submissionId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", studentCookie))
            content = submitBody
        }.andReturn()
        val submitted = tools.jackson.databind.json.JsonMapper.builder().build().readTree(submit(submitKey).response.contentAsString)
        val receiptId = submitted.get("receipt").get("id").asString()
        assertTrue(receiptId.isNotBlank())
        assertEquals(receiptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(submit(submitKey).response.contentAsString).get("receipt").get("id").asString())
        assertEquals("attached", jdbc.queryForObject("SELECT status FROM core.files WHERE id=?", String::class.java, fileId))
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; content { bytes(fileBytes) } }
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; content { bytes(fileBytes) } }
        mvc.get("/api/v1/files/$fileId/content") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", peerCookie))
        }.andExpect { status { isNotFound() } }

        mvc.get("/api/v1/submissions/$submissionId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", peerCookie))
        }.andExpect { status { isNotFound() } }
        mvc.get("/api/v1/submissions") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) }; jsonPath("$.items[0].feedbackDraft") { doesNotExist() } }

        mvc.patch("/api/v1/submissions/$submissionId/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Too high","grade":11}"""
        }.andExpect { status { isUnprocessableEntity() } }
        mvc.patch("/api/v1/submissions/$submissionId/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Good evidence","grade":9}"""
        }.andExpect { status { isOk() }; jsonPath("$.feedbackDraft.grade") { value(9) }; jsonPath("$.feedbackHistory") { isEmpty() } }
        mvc.get("/api/v1/submissions/$submissionId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.feedbackDraft") { doesNotExist() }; jsonPath("$.feedbackPublished") { value(null) } }

        mvc.post("/api/v1/submissions/$submissionId/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3}"""
        }.andExpect { status { isOk() }; jsonPath("$.feedbackHistory.length()") { value(1) } }
        mvc.patch("/api/v1/submissions/$submissionId/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":4,"comment":"Revised note","grade":null}"""
        }.andExpect { status { isOk() }; jsonPath("$.feedbackUpdated") { value(true) } }
        mvc.get("/api/v1/submissions/$submissionId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.feedbackPublished.grade") { value(9) }; jsonPath("$.feedbackUpdated") { value(true) } }
        mvc.post("/api/v1/submissions/$submissionId/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "republish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":5}"""
        }.andExpect { status { isOk() }; jsonPath("$.feedbackHistory.length()") { value(2) } }
        val gate = java.util.concurrent.CountDownLatch(1)
        val graders = Executors.newFixedThreadPool(2)
        val gradeEdits = listOf(teacherCookie to "first", otherTeacherCookie to "second").map { (cookie, comment) -> graders.submit<Int> {
            gate.await()
            mvc.patch("/api/v1/submissions/$submissionId/feedback") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                header("Origin", "https://aja.example.test")
                cookie(Cookie("aja_session", cookie))
                content = """{"expectedRevision":6,"comment":"$comment","grade":null}"""
            }.andReturn().response.status
        } }
        gate.countDown()
        assertEquals(listOf(200, 409), gradeEdits.map { it.get(10, TimeUnit.SECONDS) }.sorted())
        graders.shutdownNow()
        mvc.patch("/api/v1/submissions/$submissionId/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", otherTeacherCookie))
            content = """{"expectedRevision":6,"comment":"stale","grade":null}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun submissionReceiptSurvivesFreshCoreProcessesAndExpiredIdempotencyRecord() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (secondTeacherCookie, secondTeacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (withdrawnStudentCookie, withdrawnStudentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) SELECT subject_id,? FROM core.modules WHERE id=?", secondTeacherId, moduleId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val (withdrawnClassId, withdrawnModuleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(withdrawnClassId, withdrawnStudentId, teacherId)
        val withdrawnAssessmentId = createB07Assessment(withdrawnModuleId)
        val client = HttpClient.newHttpClient()
        var process: CoreChild? = null
        try {
            var child = startCoreProcess()
            process = child
            val createKey = "http-submission-${UUID.randomUUID()}"
            val created = coreHttp(client, child, "POST", "/submissions", studentCookie,
                """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Essay","url":"https://example.test/essay"}]}""", createKey)
            assertEquals(201, created.statusCode(), created.body())
            val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
            val id = mapper.readTree(created.body()).get("id").asString()
            val submitKey = "http-submit-${UUID.randomUUID()}"
            val submitBody = """{"expectedRevision":1}"""
            val submitted = coreHttp(client, child, "POST", "/submissions/$id/submit", studentCookie, submitBody, submitKey)
            assertEquals(200, submitted.statusCode(), submitted.body())
            val receiptId = mapper.readTree(submitted.body()).get("receipt").get("id").asString()
            stopCoreProcess(child)
            process = null

            jdbc.update("UPDATE core.command_idempotency SET created_at=created_at-interval '25 hours',expires_at=expires_at-interval '25 hours' WHERE actor_id=? AND operation='submitSubmission' AND target=? AND idempotency_key=?", studentId, "/submissions/$id/submit", submitKey)
            child = startCoreProcess()
            process = child
            val replayed = coreHttp(client, child, "POST", "/submissions/$id/submit", studentCookie, submitBody, submitKey)
            assertEquals(200, replayed.statusCode(), replayed.body())
            assertEquals(receiptId, mapper.readTree(replayed.body()).get("receipt").get("id").asString())
            val recovered = coreHttp(client, child, "GET", "/submissions/$id", studentCookie)
            assertEquals(200, recovered.statusCode(), recovered.body())
            assertEquals(receiptId, mapper.readTree(recovered.body()).get("receipt").get("id").asString())
            stopCoreProcess(child)
            process = null

            child = startCoreProcess()
            process = child
            val draft = coreHttp(client, child, "PATCH", "/submissions/$id/feedback", teacherCookie,
                """{"expectedRevision":2,"comment":"Process draft","grade":null}""")
            assertEquals(200, draft.statusCode(), draft.body())
            val draftBody = mapper.readTree(draft.body()).get("feedbackDraft")
            assertEquals(teacherId, draftBody.get("author").get("id").asString())
            val studentBeforePublish = coreHttp(client, child, "GET", "/submissions/$id", studentCookie)
            assertEquals(200, studentBeforePublish.statusCode(), studentBeforePublish.body())
            val studentDraft = mapper.readTree(studentBeforePublish.body()).get("feedbackDraft")
            assertTrue(studentDraft == null || studentDraft.isNull || studentDraft.isMissingNode)
            val published = coreHttp(client, child, "POST", "/submissions/$id/publish-feedback", secondTeacherCookie,
                """{"expectedRevision":3}""", "process-feedback-publish-${UUID.randomUUID()}")
            assertEquals(200, published.statusCode(), published.body())
            val publishedBody = mapper.readTree(published.body())
            assertEquals(secondTeacherId, publishedBody.get("feedbackPublished").get("author").get("id").asString())
            assertEquals(publishedBody.get("updatedAt").asString(), publishedBody.get("feedbackPublished").get("createdAt").asString())
            assertEquals(secondTeacherId, publishedBody.get("feedbackHistory").get(0).get("author").get("id").asString())
            val studentPublished = coreHttp(client, child, "GET", "/submissions/$id", studentCookie)
            assertEquals(secondTeacherId, mapper.readTree(studentPublished.body()).get("feedbackPublished").get("author").get("id").asString())

            val revisedDraft = coreHttp(client, child, "PATCH", "/submissions/$id/feedback", teacherCookie,
                """{"expectedRevision":4,"comment":"New private draft","grade":null}""")
            assertEquals(200, revisedDraft.statusCode(), revisedDraft.body())
            val closed = coreHttp(client, child, "POST", "/classes/$classId/close", adminCookie,
                """{"expectedRevision":1}""", "process-class-close-${UUID.randomUUID()}")
            assertEquals(200, closed.statusCode(), closed.body())
            val saveAfterClose = coreHttp(client, child, "PATCH", "/submissions/$id/feedback", secondTeacherCookie,
                """{"expectedRevision":5,"comment":"After closure","grade":null}""")
            assertEquals(409, saveAfterClose.statusCode(), saveAfterClose.body())
            val publishAfterClose = coreHttp(client, child, "POST", "/submissions/$id/publish-feedback", secondTeacherCookie,
                """{"expectedRevision":5}""", "process-closed-publish-${UUID.randomUUID()}")
            assertEquals(409, publishAfterClose.statusCode(), publishAfterClose.body())
            val historyAfterClose = coreHttp(client, child, "GET", "/submissions/$id", teacherCookie)
            assertEquals(200, historyAfterClose.statusCode(), historyAfterClose.body())
            assertEquals("New private draft", mapper.readTree(historyAfterClose.body()).get("feedbackDraft").get("comment").asString())

            val withdrawnCreated = coreHttp(client, child, "POST", "/submissions", withdrawnStudentCookie,
                """{"assessmentId":"$withdrawnAssessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/withdrawn"}]}""",
                "process-withdrawn-create-${UUID.randomUUID()}")
            assertEquals(201, withdrawnCreated.statusCode(), withdrawnCreated.body())
            val withdrawnId = mapper.readTree(withdrawnCreated.body()).get("id").asString()
            val withdrawnSubmitted = coreHttp(client, child, "POST", "/submissions/$withdrawnId/submit", withdrawnStudentCookie,
                """{"expectedRevision":1}""", "process-withdrawn-submit-${UUID.randomUUID()}")
            assertEquals(200, withdrawnSubmitted.statusCode(), withdrawnSubmitted.body())
            val withdrawnDraft = coreHttp(client, child, "PATCH", "/submissions/$withdrawnId/feedback", teacherCookie,
                """{"expectedRevision":2,"comment":"Before withdrawal","grade":null}""")
            assertEquals(200, withdrawnDraft.statusCode(), withdrawnDraft.body())
            jdbc.update("UPDATE core.modules SET withdrawn_at=? WHERE id=?", java.sql.Timestamp.from(mutableClock.instant()), withdrawnModuleId)
            val withdrawnPublish = coreHttp(client, child, "POST", "/submissions/$withdrawnId/publish-feedback", teacherCookie,
                """{"expectedRevision":3}""", "process-withdrawn-publish-${UUID.randomUUID()}")
            assertEquals(404, withdrawnPublish.statusCode(), withdrawnPublish.body())
            val withdrawnRevision = jdbc.queryForObject("SELECT revision FROM core.submissions WHERE id=?", Int::class.java, withdrawnId)!!
            val withdrawnSave = coreHttp(client, child, "PATCH", "/submissions/$withdrawnId/feedback", teacherCookie,
                """{"expectedRevision":$withdrawnRevision,"comment":"After withdrawal","grade":null}""")
            assertEquals(404, withdrawnSave.statusCode(), withdrawnSave.body())
            assertEquals(3, withdrawnRevision)
            assertEquals(0, jdbc.queryForObject("SELECT jsonb_array_length(feedback_history) FROM core.submissions WHERE id=?", Int::class.java, withdrawnId))
        } finally {
            process?.let(::stopCoreProcess)
        }
    }

    @Test
    fun preparationReplayRemainsAvailableAfterItsDeadline() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val deadline = mutableClock.instant().plusSeconds(5)
        val assessmentId = createB07Assessment(moduleId, deadline = deadline)
        val key = "retry-prep-${UUID.randomUUID()}"
        val body = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        fun prepare() = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", key)
            cookie(Cookie("aja_session", studentCookie))
            content = body
        }.andReturn()
        val initial = prepare()
        assertEquals(201, initial.response.status)
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(initial.response.contentAsString).get("id").asString()
        mutableClock.advance(Duration.ofSeconds(5))
        val replay = prepare()
        assertEquals(201, replay.response.status)
        assertEquals(id, tools.jackson.databind.json.JsonMapper.builder().build().readTree(replay.response.contentAsString).get("id").asString())
        mvc.get("/api/v1/submissions/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun closedClassRejectsFeedbackSaveAndPublication() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Draft","grade":null}"""
        }.andExpect { status { isOk() } }
        mvc.post("/api/v1/classes/$classId/close") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "close-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", adminCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }

        val publishStatus = mvc.post("/api/v1/submissions/$id/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "closed-publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3}"""
        }.andReturn().response.status
        val saveStatus = mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3,"comment":"After close","grade":null}"""
        }.andReturn().response.status
        assertEquals(409, publishStatus)
        assertEquals(409, saveStatus)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE id=? AND feedback_draft IS NOT NULL AND feedback_published IS NULL", Int::class.java, id))
    }

    @Test
    fun withdrawnModuleRejectsFeedbackSaveAndPublication() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "withdrawn-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "withdrawn-submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Draft before withdrawal","grade":null}"""
        }.andExpect { status { isOk() } }
        jdbc.update("UPDATE core.modules SET withdrawn_at=? WHERE id=?", java.sql.Timestamp.from(mutableClock.instant()), moduleId)

        val publishStatus = mvc.post("/api/v1/submissions/$id/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "withdrawn-publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3}"""
        }.andReturn().response.status
        val currentRevision = jdbc.queryForObject("SELECT revision FROM core.submissions WHERE id=?", Int::class.java, id)!!
        val saveStatus = mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":$currentRevision,"comment":"After withdrawal","grade":null}"""
        }.andReturn().response.status
        assertEquals(404 to 404, publishStatus to saveStatus)
        assertEquals(3, currentRevision)
        assertEquals(0, jdbc.queryForObject("SELECT jsonb_array_length(feedback_history) FROM core.submissions WHERE id=?", Int::class.java, id))
    }

    @Test
    fun feedbackPublicationRecordsPublisherAndPublicationTime() {
        val (adminCookie, _) = adminSession()
        val (teacherACookie, teacherAId) = userSession("teacher")
        val (teacherBCookie, teacherBId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherACookie, teacherAId)
        jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) SELECT subject_id,? FROM core.modules WHERE id=?", teacherBId, moduleId)
        createB07Enrollment(classId, studentId, teacherAId)
        val assessmentId = createB07Assessment(moduleId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "author-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "author-submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        val draft = mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherACookie))
            content = """{"expectedRevision":2,"comment":"A draft","grade":null}"""
        }.andExpect { status { isOk() } }.andReturn()
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val draftAt = mapper.readTree(draft.response.contentAsString).get("feedbackDraft").get("createdAt").asString()
        mutableClock.advance(Duration.ofSeconds(30))
        val published = mvc.post("/api/v1/submissions/$id/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "published-by-b-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherBCookie))
            content = """{"expectedRevision":3}"""
        }.andExpect { status { isOk() } }.andReturn()
        val publishedResponse = mapper.readTree(published.response.contentAsString)
        val feedback = publishedResponse.get("feedbackPublished")
        assertEquals(teacherBId, feedback.get("author").get("id").asString())
        assertEquals(publishedResponse.get("updatedAt").asString(), feedback.get("createdAt").asString())
        assertTrue(Instant.parse(feedback.get("createdAt").asString()).isAfter(Instant.parse(draftAt)))
        assertEquals(teacherBId, publishedResponse.get("feedbackHistory").get(0).get("author").get("id").asString())
        assertEquals(feedback.get("createdAt").asString(), publishedResponse.get("feedbackHistory").get(0).get("createdAt").asString())
    }

    @Test
    fun submitRechecksDeadlineAfterWaitingForAttachedFileLock() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val deadline = mutableClock.instant().plusSeconds(5)
        val assessmentId = createB07Assessment(moduleId, deadline = deadline)
        val file = mvc.perform(multipart("/api/v1/files").file(MockMultipartFile("file", "lock.txt", "text/plain", "file".toByteArray()))
            .param("purpose", "work").param("parentId", assessmentId).cookie(Cookie("aja_session", studentCookie))
            .header("Origin", "https://aja.example.test").header("Idempotency-Key", "locked-file-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(file.response.contentAsString).get("id").asString()
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "locked-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"lock.txt"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        val lockConnection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        lockConnection.autoCommit = false
        lockConnection.prepareStatement("SELECT id FROM core.files WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, fileId)
            statement.executeQuery().use { it.next() }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val submit = executor.submit<Int> {
                mvc.post("/api/v1/submissions/$id/submit") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "lock-submit-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", studentCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            awaitLockWait("FROM core.files")
            mutableClock.advance(Duration.ofSeconds(5))
            lockConnection.commit()
            assertEquals(409, submit.get(10, TimeUnit.SECONDS))
            assertEquals("staged", jdbc.queryForObject("SELECT status FROM core.files WHERE id=?", String::class.java, fileId))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE id=? AND receipt_id IS NOT NULL", Int::class.java, id))
        } finally {
            runCatching { lockConnection.rollback() }
            lockConnection.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun submitRejectsFileThatExpiresWhileWaitingForItsLock() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val file = mvc.perform(multipart("/api/v1/files").file(MockMultipartFile("file", "expires.txt", "text/plain", "file".toByteArray()))
            .param("purpose", "work").param("parentId", assessmentId).cookie(Cookie("aja_session", studentCookie))
            .header("Origin", "https://aja.example.test").header("Idempotency-Key", "expiring-file-${UUID.randomUUID()}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated).andReturn()
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(file.response.contentAsString).get("id").asString()
        jdbc.update("UPDATE core.files SET expires_at=? WHERE id=?", java.sql.Timestamp.from(mutableClock.instant().plusSeconds(5)), fileId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "expiring-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"expires.txt"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        val lockConnection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        lockConnection.autoCommit = false
        lockConnection.prepareStatement("SELECT id FROM core.files WHERE id=? FOR UPDATE").use { statement ->
            statement.setString(1, fileId)
            statement.executeQuery().use { it.next() }
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val submit = executor.submit<Int> {
                mvc.post("/api/v1/submissions/$id/submit") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "expiring-submit-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", studentCookie))
                    content = """{"expectedRevision":1}"""
                }.andReturn().response.status
            }
            awaitLockWait("FROM core.files")
            mutableClock.advance(Duration.ofSeconds(5))
            lockConnection.commit()
            assertEquals(409, submit.get(10, TimeUnit.SECONDS))
            assertEquals("staged", jdbc.queryForObject("SELECT status FROM core.files WHERE id=?", String::class.java, fileId))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE id=? AND receipt_id IS NOT NULL", Int::class.java, id))
        } finally {
            runCatching { lockConnection.rollback() }
            lockConnection.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun emptySubmissionCanBeResumedButCannotBeSent() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "empty-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "empty-submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE id=? AND submitted_at IS NOT NULL", Int::class.java, id))
        mvc.patch("/api/v1/submissions/$id") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1,"attachments":[{"kind":"link","name":"Final","url":"https://example.test/final"}]}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(2) } }
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "recovered-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":2}"""
        }.andExpect { status { isOk() }; jsonPath("$.receipt.id") { exists() } }
    }

    @Test
    fun submissionDeadlineIsExclusiveAtCommit() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val deadline = mutableClock.instant().plusSeconds(5)
        val assessmentId = createB07Assessment(moduleId, deadline = deadline)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "deadline-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mutableClock.advance(Duration.ofSeconds(5))
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "late-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE id=? AND receipt_id IS NOT NULL", Int::class.java, id))
    }

    @Test
    fun revokedTeacherCannotPublishSubmissionFeedback() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val created = mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "revoked-create-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Work","url":"https://example.test/work"}]}"""
        }.andExpect { status { isCreated() } }.andReturn()
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.response.contentAsString).get("id").asString()
        mvc.post("/api/v1/submissions/$id/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "revoked-submit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1}"""
        }.andExpect { status { isOk() } }
        mvc.patch("/api/v1/submissions/$id/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Private draft","grade":null}"""
        }.andExpect { status { isOk() } }
        jdbc.update("UPDATE core.sessions SET revoked_at=? WHERE token_hash=?", java.sql.Timestamp.from(mutableClock.instant()), sha256(teacherCookie))
        mvc.post("/api/v1/submissions/$id/publish-feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "revoked-publish-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":3}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.get("/api/v1/submissions/$id") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.feedbackPublished") { value(null) }; jsonPath("$.feedbackDraft") { doesNotExist() } }
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
    fun assessmentCreationQueuedBehindRevocationCannotCommit() {
        val adminCookie = adminSession().first
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val held = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)
        held.autoCommit = false
        held.createStatement().use { it.execute("SELECT pg_advisory_xact_lock(812734)") }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val logout = pool.submit<Int> {
                mvc.post("/api/v1/auth/logout") { header("Origin", "https://aja.example.test"); cookie(Cookie("aja_session", teacherCookie)) }.andReturn().response.status
            }
            awaitLockWaitCount(1)
            val create = pool.submit<Int> {
                mvc.post("/api/v1/assessments") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    header("Origin", "https://aja.example.test")
                    header("Idempotency-Key", "revoked-${UUID.randomUUID()}")
                    cookie(Cookie("aja_session", teacherCookie))
                    content = """{"moduleId":"$moduleId","title":"Revoked activity","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
                }.andReturn().response.status
            }
            repeat(80) { if (!create.isDone) Thread.sleep(25) }
            assertFalse(create.isDone, "Assessment writes wait for the Core authorization gate")
            awaitLockWaitCount(2)
            held.commit()
            assertEquals(204, logout.get(10, TimeUnit.SECONDS))
            assertEquals(403, create.get(10, TimeUnit.SECONDS))
        } finally {
            runCatching { held.rollback() }
            held.close()
            pool.shutdownNow()
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.assessments WHERE title='Revoked activity'", Int::class.java))
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
        assertEquals(16, jdbc.queryForObject("select count(*) from core.flyway_schema_history where success", Int::class.java))
    }

    @Test
    fun attendanceCanBeCreatedAndReplayedWithoutDuplicatingTheClassSession() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (unassignedCookie, _) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val (peerCookie, peerId) = userSession("student")
        createB07Enrollment(classId, peerId, teacherId)
        val body = """{"subjectId":"$subjectId","date":"2026-11-12","time":"14:00","records":{"$studentId":true}}"""
        val key = "attendance-${UUID.randomUUID()}"

        val created = liveHttp("POST", "/attendance", teacherCookie, body, key)
        assertEquals(201, created.statusCode(), created.body())
        val replay = liveHttp("POST", "/attendance", teacherCookie, body, key)
        assertEquals(201, replay.statusCode(), replay.body())
        assertEquals(409, liveHttp("POST", "/attendance", teacherCookie, body.replace(":true", ":false"), key).statusCode())
        assertEquals(409, liveHttp("POST", "/attendance", teacherCookie, body, "attendance-new-${UUID.randomUUID()}").statusCode())
        assertEquals(422, liveHttp("POST", "/attendance", teacherCookie, body).statusCode())
        assertEquals(422, liveHttp("POST", "/attendance", teacherCookie, body.replace("2026-11-12", "2026-13-12"), "attendance-date-${UUID.randomUUID()}").statusCode())
        val json = tools.jackson.databind.json.JsonMapper.builder().build()
        val id = json.readTree(created.body()).get("id").asText()
        assertEquals(id, json.readTree(replay.body()).get("id").asText())
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attendance WHERE subject_id=? AND class_date='2026-11-12' AND class_time='14:00'", Int::class.java, subjectId))
        val recordIds = listOf(studentId, peerId)
        val mapOrderOne = """{"subjectId":"$subjectId","date":"2026-11-13","time":"14:00","records":{"${recordIds[0]}":true,"${recordIds[1]}":false}}"""
        val mapOrderTwo = """{"subjectId":"$subjectId","date":"2026-11-13","time":"14:00","records":{"${recordIds[1]}":false,"${recordIds[0]}":true}}"""
        val mapKey = "attendance-${UUID.randomUUID()}"
        val mapCreated = json.readTree(liveHttp("POST", "/attendance", teacherCookie, mapOrderOne, mapKey).body())
        val mapReplay = json.readTree(liveHttp("POST", "/attendance", teacherCookie, mapOrderTwo, mapKey).body())
        assertEquals(mapCreated.get("id").asText(), mapReplay.get("id").asText())

        val eligible = liveHttp("GET", "/attendance/eligible?subjectId=$subjectId&date=2026-11-12&time=14:00", teacherCookie)
        assertEquals(200, eligible.statusCode(), eligible.body())
        assertTrue(json.readTree(eligible.body()).get("items").first { it.get("id").asText() == studentId }.get("eligible").asBoolean())
        val teacherListResponse = liveHttp("GET", "/attendance?subjectId=$subjectId&from=2026-11-12&to=2026-11-12&sort=-date", teacherCookie)
        assertEquals(200, teacherListResponse.statusCode(), teacherListResponse.body())
        val teacherList = json.readTree(teacherListResponse.body())
        assertEquals(1, teacherList.get("total").asInt())
        assertTrue(teacherList.get("items").first().get("records").get(studentId).asBoolean())
        val studentListResponse = liveHttp("GET", "/attendance?subjectId=$subjectId&studentId=$studentId&from=2026-11-12&to=2026-11-12", studentCookie)
        assertEquals(200, studentListResponse.statusCode(), studentListResponse.body())
        val studentList = json.readTree(studentListResponse.body())
        assertTrue(studentList.get("items").first().get("present").asBoolean())
        assertEquals(null, studentList.get("items").first().get("records"))
        assertEquals(0, json.readTree(liveHttp("GET", "/attendance?subjectId=$subjectId&studentId=$studentId", peerCookie).body()).get("total").asInt())
        val teacherDetail = json.readTree(liveHttp("GET", "/attendance/$id", teacherCookie).body())
        assertTrue(teacherDetail.get("records").get(studentId).asBoolean())
        assertEquals(404, liveHttp("GET", "/attendance/$id", unassignedCookie).statusCode())
        assertEquals(403, liveHttp("POST", "/attendance", unassignedCookie, body, "attendance-unassigned-${UUID.randomUUID()}").statusCode())
        val studentDetail = json.readTree(liveHttp("GET", "/attendance/$id", studentCookie).body())
        assertTrue(studentDetail.get("present").asBoolean())
        assertEquals(null, studentDetail.get("records"))
        val peerDetail = json.readTree(liveHttp("GET", "/attendance/$id", peerCookie).body())
        assertTrue(peerDetail.get("present").isNull)

        val updated = liveHttp("PATCH", "/attendance/$id", teacherCookie, """{"expectedRevision":1,"records":{"$studentId":false}}""")
        assertEquals(200, updated.statusCode(), updated.body())
        val conflict = json.readTree(liveHttp("PATCH", "/attendance/$id", teacherCookie, """{"expectedRevision":1,"records":{"$studentId":true}}""").body())
        assertEquals(2, conflict.get("error").get("details").get("currentRevision").asInt())
        val peerWrite = liveHttp("PATCH", "/attendance/$id", peerCookie, """{"expectedRevision":2,"records":{"$studentId":true}}""")
        assertEquals(403, peerWrite.statusCode())
        jdbc.update("UPDATE core.classes SET active=false WHERE id=?", classId)
        assertEquals(403, liveHttp("PATCH", "/attendance/$id", teacherCookie, """{"expectedRevision":2,"records":{"$studentId":true}}""").statusCode())
        assertEquals(403, liveHttp("POST", "/attendance", teacherCookie, body, key).statusCode())
    }

    @Test
    fun attendanceSerializesConcurrentCreationAndRespectsEnrollmentDatesAndLegacyRows() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val (_, currentId) = userSession("student")
        createB07Enrollment(classId, currentId, teacherId)
        val (_, futureId) = userSession("student")
        createB07Enrollment(classId, futureId, teacherId, enrolledAt = java.time.Instant.parse("2026-11-13T03:00:00Z"))
        val (_, exitedId) = userSession("student")
        val exitedEnrollment = createB07Enrollment(classId, exitedId, teacherId)
        jdbc.update("UPDATE core.enrollments SET status='ended',ended_at=? WHERE id=?", java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T02:00:00Z")), exitedEnrollment)
        val (legacyCookie, legacyId) = userSession("student")
        val legacyEnrollment = createB07Enrollment(classId, legacyId, teacherId, enrolledAt = null)
        jdbc.update("UPDATE core.enrollments SET legacy=true WHERE id=?", legacyEnrollment)
        val (cancelledCookie, cancelledId) = userSession("student")
        val cancelledEnrollment = createB07Enrollment(classId, cancelledId, teacherId)
        jdbc.update("UPDATE core.enrollments SET status='cancelled' WHERE id=?", cancelledEnrollment)

        val eligible = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/attendance/eligible?subjectId=$subjectId&date=2026-11-12&time=14:00", teacherCookie).body()).get("items")
        fun byId(id: String) = eligible.first { it.get("id").asText() == id }
        assertTrue(byId(currentId).get("eligible").asBoolean())
        assertEquals("beforeEnrollment", byId(futureId).get("reason").asText())
        assertEquals("afterExit", byId(exitedId).get("reason").asText())
        assertFalse(byId(legacyId).get("eligible").asBoolean())
        assertEquals("legacy", byId(legacyId).get("reason").asText())
        val historicalLegacyId = UUID.randomUUID().toString()
        val legacyTime = java.sql.Timestamp.from(java.time.Instant.parse("2026-11-12T16:00:00Z"))
        jdbc.update("INSERT INTO core.attendance(id,revision,created_at,updated_at,updated_by,subject_id,class_date,class_time,records) VALUES(?,1,?,?,?,?,?,?,?::jsonb)", historicalLegacyId, legacyTime, legacyTime, teacherId, subjectId, java.time.LocalDate.parse("2026-11-12"), java.time.LocalTime.parse("13:00"), "{\"$legacyId\":true}")
        assertEquals(true, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/attendance/$historicalLegacyId", legacyCookie).body()).get("present").asBoolean())
        assertEquals(true, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/attendance/$historicalLegacyId", adminCookie).body()).get("records").get(legacyId).asBoolean())
        val legacyPatch = liveHttp("PATCH", "/attendance/$historicalLegacyId", teacherCookie, """{"expectedRevision":1,"records":{}}""")
        assertEquals(200, legacyPatch.statusCode(), legacyPatch.body())
        assertEquals(true, tools.jackson.databind.json.JsonMapper.builder().build().readTree(legacyPatch.body()).get("records").get(legacyId).asBoolean())
        val historicalCount = jdbc.queryForObject("SELECT count(*) FROM core.attendance", Int::class.java)
        val newLegacy = liveHttp("POST", "/attendance", teacherCookie,
            """{"subjectId":"$subjectId","date":"2026-11-12","time":"16:30","records":{"$legacyId":true}}""",
            "attendance-legacy-new-${UUID.randomUUID()}")
        assertEquals(422, newLegacy.statusCode(), newLegacy.body())
        assertEquals(historicalCount, jdbc.queryForObject("SELECT count(*) FROM core.attendance", Int::class.java))
        val rejectedBefore = jdbc.queryForObject("SELECT count(*) FROM core.attendance", Int::class.java)
        assertEquals(rejectedBefore, jdbc.queryForObject("SELECT count(*) FROM core.attendance", Int::class.java))
        val rejected = liveHttp("POST", "/attendance", teacherCookie,
            """{"subjectId":"$subjectId","date":"2026-11-12","time":"16:00","records":{"$futureId":true}}""",
            "attendance-ineligible-${UUID.randomUUID()}")
        assertEquals(422, rejected.statusCode(), rejected.body())
        assertEquals(rejectedBefore, jdbc.queryForObject("SELECT count(*) FROM core.attendance", Int::class.java))

        val pool = Executors.newFixedThreadPool(2)
        try {
            val gate = CountDownLatch(1)
            val first = pool.submit<HttpResponse<String>> { gate.await(); liveHttp("POST", "/attendance", teacherCookie, """{"subjectId":"$subjectId","date":"2026-11-12","time":"15:00","records":{"$currentId":true}}""", "attendance-a-${UUID.randomUUID()}") }
            val second = pool.submit<HttpResponse<String>> { gate.await(); liveHttp("POST", "/attendance", teacherCookie, """{"subjectId":"$subjectId","date":"2026-11-12","time":"15:00","records":{"$currentId":false}}""", "attendance-b-${UUID.randomUUID()}") }
            gate.countDown()
            val responses = listOf(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
            val statuses = responses.map { it.statusCode() }.sorted()
            assertEquals(listOf(201, 409), statuses)
            val sessionId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(responses.single { it.statusCode() == 201 }.body()).get("id").asText()
            assertEquals(404, liveHttp("GET", "/attendance/$sessionId", cancelledCookie).statusCode())
        } finally { pool.shutdownNow() }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attendance WHERE subject_id=? AND class_date='2026-11-12' AND class_time='15:00'", Int::class.java, subjectId))
    }

    @Test
    fun attendanceWriteRechecksTeacherAssignmentAtCommitTime() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val (_, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val body = """{"subjectId":"$subjectId","date":"2026-11-12","time":"17:00","records":{"$studentId":true}}"""
        val key = "attendance-${UUID.randomUUID()}"
        val created = liveHttp("POST", "/attendance", teacherCookie, body, key)
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        jdbc.update("DELETE FROM core.subject_teachers WHERE subject_id=? AND teacher_id=?", subjectId, teacherId)
        assertEquals(403, liveHttp("PATCH", "/attendance/$id", teacherCookie, """{"expectedRevision":1,"records":{"$studentId":false}}""").statusCode())
        assertEquals(404, liveHttp("GET", "/attendance/$id", teacherCookie).statusCode())
        assertEquals(403, liveHttp("POST", "/attendance", teacherCookie, body, key).statusCode())
        assertEquals(200, liveHttp("GET", "/attendance/$id", adminCookie).statusCode())
    }

    @Test
    fun attendanceListUsesBoundedPageProjectionQueries() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val (_, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        repeat(12) { index ->
            val date = java.time.LocalDate.parse("2026-11-12").plusDays(index.toLong())
            val instant = date.atTime(16, 0).toInstant(java.time.ZoneOffset.UTC)
            val timestamp = java.sql.Timestamp.from(instant)
            jdbc.update("INSERT INTO core.attendance(id,revision,created_at,updated_at,updated_by,subject_id,class_date,class_time,records) VALUES(?,1,?,?,?,?,?,?,?::jsonb)", UUID.randomUUID().toString(), timestamp, timestamp, teacherId, subjectId, date, java.time.LocalTime.parse("13:00"), "{\"$studentId\":true}")
        }
        val delegate = checkNotNull(jdbc.dataSource)
        val queryCount = java.util.concurrent.atomic.AtomicInteger()
        val countingDataSource = object : DataSource by delegate {
            override fun getConnection(): Connection {
                val connection = delegate.connection
                return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
                    if (method.name == "prepareStatement") queryCount.incrementAndGet()
                    try { method.invoke(connection, *(args ?: emptyArray())) }
                    catch (error: InvocationTargetException) { throw error.targetException }
                } as Connection
            }
            override fun getConnection(username: String, password: String): Connection = getConnection()
        }
        val store = JdbcAttendanceStore(JdbcTemplate(countingDataSource), tools.jackson.databind.json.JsonMapper.builder().build(), mutableClock)
        val page = store.list(teacherId, "teacher", sha256(teacherCookie), 1, 10, "date", subjectId, null, null, null)
        assertEquals(10, page.items.size)
        assertEquals(12, page.total)
        assertTrue(queryCount.get() <= 5, "Expected at most five SQL statements for a page, observed ${queryCount.get()}")
    }

    private fun assertPrivilegeDenied(action: () -> Unit) {
        val error = assertThrows(DataAccessException::class.java, action)
        assertTrue(generateSequence(error as Throwable?) { it.cause }.filterIsInstance<SQLException>().any { it.sqlState == "42501" })
    }

    private fun liveHttp(method: String, path: String, cookie: String, body: String? = null, key: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${checkNotNull(webContext.webServer).port}/api/v1$path"))
            .header("Origin", "https://aja.example.test")
            .header("Cookie", "aja_session=$cookie")
            .header("Content-Type", "application/json")
        key?.let { builder.header("Idempotency-Key", it) }
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return HttpClient.newHttpClient().send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }

    private data class CoreChild(val process: Process, val port: Int)

    private data class QuestionsChild(val process: Process, val port: Int)

    private fun startQuestionsProcess(coreInternalSessionUrl: String? = null): QuestionsChild {
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

    private fun stopQuestionsProcess(child: QuestionsChild) {
        child.process.destroy()
        if (!child.process.waitFor(10, TimeUnit.SECONDS)) {
            child.process.destroyForcibly()
            assertTrue(child.process.waitFor(10, TimeUnit.SECONDS), "Questions child process did not terminate")
        }
    }

    private fun startCoreProcess(questionSnapshotsUrl: String? = null): CoreChild {
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

    private fun stopCoreProcess(child: CoreChild) {
        child.process.destroy()
        if (!child.process.waitFor(10, TimeUnit.SECONDS)) {
            child.process.destroyForcibly()
            assertTrue(child.process.waitFor(10, TimeUnit.SECONDS), "Core child process did not terminate")
        }
    }

    private fun coreHttp(
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
        val authorId = jdbc.queryForObject("SELECT updated_by FROM core.modules WHERE id=?", String::class.java, moduleId)!!
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description)
            VALUES(?,?,?,?,1,?,?,?,?,?,?,?,?)""", id, moduleId, kind, status, publishedAt, deadline?.let(java.sql.Timestamp::from), allowLate, now, now, authorId, "B07 fixture", "")
        return id
    }

    private fun createAttemptQuiz(moduleId: String, deadline: Instant, allowLate: Boolean): String {
        val assessmentId = createB07Assessment(moduleId, kind = "quiz", allowLate = allowLate, deadline = deadline)
        val questionId = "attempt-question-${UUID.randomUUID()}"
        val snapshot = """{"id":"$questionId","origin":"author","subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"1 + 1?","options":["2","3"],"image":null,"correct":0,"explanation":"Because"}"""
        jdbc.update("""INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot)
            VALUES(?,?,0,'author',1,NULL,?::jsonb)""", assessmentId, questionId, snapshot)
        return assessmentId
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
        val eventId = jdbc.queryForObject("select id from core.outbox_events where event_type = ? order by created_at desc limit 1", String::class.java, eventType)!!
        return outboxPayload(eventId, eventType)
    }

    private fun outboxPayload(eventId: String, eventType: String): String {
        val event = jdbc.queryForMap("select nonce, encrypted_payload from core.outbox_events where id=? AND event_type=?", eventId, eventType)
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
            channel.queueBind("email.identity.events", "aja.events", "academic.announcement-notification-requested")
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
