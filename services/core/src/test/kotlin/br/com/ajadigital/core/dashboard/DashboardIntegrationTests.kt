package br.com.ajadigital.core.dashboard
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import jakarta.servlet.http.Cookie
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import javax.sql.DataSource
import java.util.UUID
import java.time.Instant
import br.com.ajadigital.core.announcements.adapters.out.persistence.JdbcAnnouncementStore
import br.com.ajadigital.core.academic.adapters.out.persistence.JdbcAcademicStore
import br.com.ajadigital.core.forum.adapters.out.persistence.JdbcTopicStore
import br.com.ajadigital.core.outbox.adapters.out.security.AesGcmOutboxPayloadProtector

class DashboardIntegrationTests : CoreIntegrationTestBase() {
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
    fun teacherClassReadsIncludeAssignedArchiveAndHideOtherAssignments() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, otherTeacherId) = userSession("teacher")
        val assignedClassId = createB06Class(adminCookie)
        val archivedClassId = createB06Class(adminCookie)
        val otherTeacherClassId = createB06Class(adminCookie)
        val unassignedClassId = createB06Class(adminCookie)
        val (_, assignedStudentId) = userSession("student")
        val (_, historicalStudentId) = userSession("student")
        val (_, otherTeacherStudentId) = userSession("student")
        createB06Subject(adminCookie, assignedClassId, teacherId)
        createB06Subject(adminCookie, archivedClassId, teacherId)
        createB06Subject(adminCookie, otherTeacherClassId, otherTeacherId)
        createB07Enrollment(assignedClassId, assignedStudentId, teacherId)
        val historicalEnrollmentId = createB07Enrollment(archivedClassId, historicalStudentId, teacherId)
        jdbc.update("UPDATE core.enrollments SET status='ended',ended_at=? WHERE id=?", java.sql.Timestamp.from(mutableClock.instant()), historicalEnrollmentId)
        createB07Enrollment(otherTeacherClassId, otherTeacherStudentId, otherTeacherId)
        val pendingEmail = "pending-${UUID.randomUUID()}@aja.local.test"
        assertEquals(201, liveHttp("POST", "/enrollments", adminCookie, """{"classId":"$assignedClassId","email":"$pendingEmail"}""", "pending-${UUID.randomUUID()}").statusCode())
        assertEquals(200, liveHttp("POST", "/classes/$archivedClassId/close", adminCookie, """{"expectedRevision":1}""", "close-${UUID.randomUUID()}").statusCode())

        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val classes = mapper.readTree(liveHttp("GET", "/classes?page=1&pageSize=100", teacherCookie).body())
        assertEquals(2, classes.get("total").intValue(), classes.toString())
        assertEquals(setOf(assignedClassId, archivedClassId), classes.get("items").mapNotNull { it.get("id")?.asText() }.toSet(), classes.toString())
        assertEquals(200, liveHttp("GET", "/classes/$assignedClassId", teacherCookie).statusCode())
        assertEquals(200, liveHttp("GET", "/classes/$archivedClassId", teacherCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/classes/$otherTeacherClassId", teacherCookie).statusCode())
        assertEquals(404, liveHttp("GET", "/classes/$unassignedClassId", teacherCookie).statusCode())

        val students = mapper.readTree(liveHttp("GET", "/students?classId=$assignedClassId&page=1&pageSize=100", teacherCookie).body())
        assertEquals(1, students.get("total").intValue(), students.toString())
        assertEquals(setOf(assignedStudentId), students.get("items").mapNotNull { it.get("id")?.asText() }.toSet(), students.toString())
        val studentHistory = mapper.readTree(liveHttp("GET", "/students?classId=$archivedClassId&history=true&page=1&pageSize=100", teacherCookie).body())
        assertEquals(setOf(historicalStudentId), studentHistory.get("items").mapNotNull { it.get("id")?.asText() }.toSet(), studentHistory.toString())
        assertEquals(200, liveHttp("GET", "/students/$assignedStudentId", teacherCookie).statusCode())
        val historicalDetail = mapper.readTree(liveHttp("GET", "/students/$historicalStudentId", teacherCookie).body())
        assertEquals(setOf(historicalEnrollmentId), historicalDetail.get("enrollments").mapNotNull { it.get("id")?.asText() }.toSet(), historicalDetail.toString())
        assertEquals(404, liveHttp("GET", "/students/$otherTeacherStudentId", teacherCookie).statusCode())

        val enrollments = mapper.readTree(liveHttp("GET", "/enrollments?classId=$assignedClassId&page=1&pageSize=100", teacherCookie).body())
        assertEquals(1, enrollments.get("total").intValue(), enrollments.toString())
        assertEquals(setOf(assignedStudentId), enrollments.get("items").mapNotNull { it.get("studentId")?.asText() }.toSet(), enrollments.toString())
        assertTrue(enrollments.get("items").none { it.get("email").asText() == pendingEmail }, enrollments.toString())
        assertEquals(404, liveHttp("GET", "/enrollments?classId=$otherTeacherClassId&page=1&pageSize=100", teacherCookie).statusCode())

        val dashboard = mapper.readTree(liveHttp("GET", "/me/dashboard", teacherCookie).body())
        assertEquals(setOf(assignedClassId), dashboard.get("classes").mapNotNull { it.get("id")?.asText() }.toSet(), dashboard.toString())
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
        assertEquals(1, store.dashboardTopics(br.com.ajadigital.core.forum.application.models.ListDashboardStoredTopicsQuery(teacherId, "teacher", sha256(teacherCookie), 5)).size)
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
            br.com.ajadigital.core.outbox.application.usecases.OutboxEventProducerUseCase(
                AesGcmOutboxPayloadProtector(java.util.Base64.getEncoder().encodeToString(ByteArray(32))),
                br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxEventWriter(JdbcTemplate(countingDataSource)),
            ),
        )
        val sessionHash = sha256(studentCookie)
        repeat(3) { createB06Class(adminCookie) }
        capturedSql.clear()
        val classPage = JdbcAcademicStore(JdbcTemplate(countingDataSource), mutableClock).dashboardClasses(br.com.ajadigital.core.academic.application.models.AcademicStoreDashboardClassesDto(adminId, "admin", 100))
        assertTrue(classPage.size >= 3)
        assertTrue(classPage.all { it.studentCount >= 0 })
        val classQueries = capturedSql.filter { it.contains("FROM core.classes", ignoreCase = true) }
        assertEquals(1, classQueries.size, classQueries.toString())
        assertTrue(Regex("\\bLIMIT\\s+\\?", RegexOption.IGNORE_CASE).containsMatchIn(classQueries.single()))
        capturedSql.clear()
        val notices = store.dashboardAnnouncements(br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementStoreQuery(studentId, "student", sessionHash, 5))
        val upcoming = store.dashboardUpcoming(br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingStoreQuery(studentId, "student", sessionHash, "2026-11-12", "2026-12-12", 5))
        assertEquals(5, notices.size)
        assertEquals(5, upcoming.size)
        val announcementQueries = capturedSql.filter { it.contains("FROM core.announcements", ignoreCase = true) }
        assertEquals(2, announcementQueries.size, announcementQueries.toString())
        assertTrue(announcementQueries.all { Regex("\\bLIMIT\\s+\\?", RegexOption.IGNORE_CASE).containsMatchIn(it) }, announcementQueries.joinToString("\n"))
    }
}
