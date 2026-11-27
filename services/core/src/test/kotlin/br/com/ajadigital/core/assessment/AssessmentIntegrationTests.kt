package br.com.ajadigital.core.assessment
import br.com.ajadigital.core.CoreIntegrationTestBase
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.NotFound
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.MvcResult
import jakarta.servlet.http.Cookie
import java.sql.DriverManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.Instant
import java.security.MessageDigest

class AssessmentIntegrationTests : CoreIntegrationTestBase() {
    @Autowired private lateinit var actors: ResolveAuthenticatedActorInputPort
    @Autowired private lateinit var getAssessment: GetAssessmentInputPort
    @Autowired private lateinit var listAssessments: ListAssessmentsInputPort

    @Test
    fun assessmentReadUsesCurrentRoleForAnAlreadyResolvedActor() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val body = """{"moduleId":"$moduleId","title":"Assess access","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
        val created = liveHttp("POST", "/assessments", teacherCookie, body, "create-${UUID.randomUUID()}")
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asText()
        val retainedActor = actors.resolve(adminCookie)
        val query = GetAssessmentQuery(retainedActor, id)
        val listing = ListAssessmentsQuery(retainedActor, 1, 100, "id", null, moduleId, null, null)
        assertEquals(id, getAssessment.assessment(query).record.id)
        assertEquals(listOf(id), listAssessments.assessments(listing).items.map { it.record.id })
        val lastPage = listAssessments.assessments(listing.copy(page = Int.MAX_VALUE))
        assertEquals(1L, lastPage.total)
        assertTrue(lastPage.items.isEmpty())

        assertEquals(1, jdbc.update("UPDATE core.users SET role='teacher' WHERE id=?", adminId))
        try {
            assertEquals("teacher", actors.resolve(adminCookie).role)
            assertThrows(NotFound::class.java) { getAssessment.assessment(query) }
            assertEquals(0L, listAssessments.assessments(listing).total)
        } finally {
            jdbc.update("UPDATE core.users SET role='admin' WHERE id=?", adminId)
        }
    }

    @Test
    fun assessmentSnapshotFetchLeavesLocksFreeAndRechecksRevisionAtCommit() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val body = """{"moduleId":"$moduleId","title":"Snapshot race","description":"","kind":"quiz","maxGrade":10.0,"deadline":null,"required":false,"allowLate":false,"questions":[{"source":"bank","questionId":"race-existing"}]}"""
        val response = liveHttp("POST", "/assessments", teacherCookie, body, "initial-${UUID.randomUUID()}")
        assertEquals(201, response.statusCode(), response.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body()).get("id").stringValue()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val pendingKey = "pending-${UUID.randomUUID()}"
        try {
            questionSnapshotProvider.blockNextSnapshot(started, release)
            val pending = pool.submit<HttpResponse<String>> {
                liveHttp("PATCH", "/assessments/$id", teacherCookie, """{"expectedRevision":1,"confirmResetDrafts":true,"questions":[{"source":"bank","questionId":"race-new"}]}""", pendingKey)
            }
            assertTrue(started.await(10, TimeUnit.SECONDS), "Update did not reach snapshot provider")
            val winner = pool.submit<HttpResponse<String>> {
                liveHttp("PATCH", "/assessments/$id", teacherCookie, """{"expectedRevision":1,"title":"Committed first"}""", "winner-${UUID.randomUUID()}")
            }.get(10, TimeUnit.SECONDS)
            assertEquals(200, winner.statusCode(), winner.body())
            release.countDown()
            val stale = pending.get(10, TimeUnit.SECONDS)
            assertEquals(409, stale.statusCode(), stale.body())
            assertEquals(2, jdbc.queryForObject("SELECT revision FROM core.assessments WHERE id=?", Int::class.java, id))
            assertEquals("race-existing", jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, id))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.command_idempotency WHERE idempotency_key=?", Int::class.java, pendingKey))
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun coreAuthorsAgainstTheRealAuthenticatedQuestionsSnapshotEndpoint() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (_, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val questions = startQuestionsProcess()
        try {
            val id = "authoring-${UUID.randomUUID()}"
            DriverManager.getConnection("${postgres.jdbcUrl}&currentSchema=questions", "questions", "questions-test").use { connection ->
                connection.prepareStatement("""INSERT INTO questions.question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation)
                    VALUES (?,4,now(),'Math','Algebra','easy','AJA',2026,'Original catalogue item','{2,3}',0,'Because')""").use {
                    it.setString(1, id)
                    assertEquals(1, it.executeUpdate())
                }
            }
            val core = startCoreProcess("http://127.0.0.1:${questions.port}/internal/v1/snapshots")
            try {
                val key = "bank-create-${UUID.randomUUID()}"
                val body = """{"moduleId":"$moduleId","title":"Catalogue quiz","description":"","kind":"quiz","maxGrade":10.0,"deadline":null,"required":false,"allowLate":false,"questions":[{"source":"bank","questionId":"$id"}]}"""
                val client = HttpClient.newHttpClient()
                fun create(value: String): HttpResponse<String> = client.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:${core.port}/api/v1/assessments"))
                        .header("Origin", "https://aja.example.test")
                        .header("Cookie", "aja_session=$teacherCookie")
                        .header("Idempotency-Key", key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(value)).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                val created = create(body)
                assertEquals(201, created.statusCode(), created.body())
                val assessmentId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").stringValue()
                assertEquals(4, jdbc.queryForObject("SELECT source_revision FROM core.assessment_questions WHERE assessment_id=?", Int::class.java, assessmentId))
                assertTrue(jdbc.queryForObject("SELECT content_hash FROM core.assessment_questions WHERE assessment_id=?", String::class.java, assessmentId).orEmpty().matches(Regex("[a-f0-9]{64}")))
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
                assertEquals(fingerprint, jdbc.queryForObject("SELECT request_hash FROM core.command_idempotency WHERE idempotency_key=?", String::class.java, key))
                assertEquals(created.body(), jdbc.queryForObject("SELECT response_body FROM core.command_idempotency WHERE idempotency_key=?", String::class.java, key))
                val question = """"questions":[{"id":"$id","year":2026,"image":null,"topic":"Algebra","origin":"bank","correct":0,"options":["2","3"],"subject":"Math","statement":"Original catalogue item","difficulty":"easy","explanation":"Because","institution":"AJA"}]"""
                assertTrue(created.body().contains(question), created.body())
                val replay = create(body)
                assertEquals(201, replay.statusCode(), replay.body())
                assertEquals(created.body(), replay.body())
                val changedNumber = create(body.replace("10.0", "10"))
                assertEquals(409, changedNumber.statusCode(), changedNumber.body())
            } finally {
                stopCoreProcess(core)
            }
        } finally {
            stopQuestionsProcess(questions)
        }
    }

    @Test
    fun assessmentPatchDistinguishesAbsentConfirmationFromExplicitNullOverHttpAndPostgres() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (_, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId, kind = "work", status = "draft", deadline = mutableClock.instant().plusSeconds(600))
        assertTrue(jdbc.queryForObject("SELECT deadline FROM core.assessments WHERE id=?", java.sql.Timestamp::class.java, assessmentId) != null)

        val explicitNull = liveHttp(
            "PATCH", "/assessments/$assessmentId", teacherCookie,
            """{"expectedRevision":1,"confirmResetDrafts":null}""",
            "confirm-null-${UUID.randomUUID()}",
        )
        assertEquals(422, explicitNull.statusCode(), explicitNull.body())
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM core.assessments WHERE id=?", Int::class.java, assessmentId))

        val absent = liveHttp("PATCH", "/assessments/$assessmentId", teacherCookie, """{"expectedRevision":1}""", "confirm-absent-${UUID.randomUUID()}")
        assertEquals(200, absent.statusCode(), absent.body())
        assertEquals(2, jdbc.queryForObject("SELECT revision FROM core.assessments WHERE id=?", Int::class.java, assessmentId))

        val falseValue = liveHttp("PATCH", "/assessments/$assessmentId", teacherCookie, """{"expectedRevision":2,"confirmResetDrafts":false}""", "confirm-false-${UUID.randomUUID()}")
        assertEquals(200, falseValue.statusCode(), falseValue.body())
        val trueValue = liveHttp("PATCH", "/assessments/$assessmentId", teacherCookie, """{"expectedRevision":3,"confirmResetDrafts":true}""", "confirm-true-${UUID.randomUUID()}")
        assertEquals(200, trueValue.statusCode(), trueValue.body())

        val (studentCookie, _) = userSession("student")
        val forbiddenNull = liveHttp("PATCH", "/assessments/$assessmentId", studentCookie, """{"expectedRevision":4,"confirmResetDrafts":null}""", "confirm-forbidden-${UUID.randomUUID()}")
        assertEquals(403, forbiddenNull.statusCode(), forbiddenNull.body())
        val missingRevision = liveHttp("PATCH", "/assessments/$assessmentId", teacherCookie, """{"confirmResetDrafts":false}""", "confirm-revision-${UUID.randomUUID()}")
        assertEquals(422, missingRevision.statusCode(), missingRevision.body())
        val removeDeadline = liveHttp("PATCH", "/assessments/$assessmentId", teacherCookie, """{"expectedRevision":4,"deadline":null}""", "confirm-deadline-${UUID.randomUUID()}")
        assertEquals(200, removeDeadline.statusCode(), removeDeadline.body())
        assertEquals(null, jdbc.queryForObject("SELECT deadline FROM core.assessments WHERE id=?", java.sql.Timestamp::class.java, assessmentId))
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
                    .header("Origin", "https://aja.example.test")
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
    fun assessmentCanBeCreatedReadAndPublishedByItsAssignedTeacher() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId)
        val createKey = "assessment-${UUID.randomUUID()}"
        val createBody = """{"moduleId":"$moduleId","title":"B08 work","description":"","kind":"work","maxGrade":null,"deadline":null,"required":false,"allowLate":false,"questions":[]}"""
        val created = liveHttp("POST", "/assessments", teacherCookie, createBody, createKey)
        assertEquals(201, created.statusCode(), created.body())
        val body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body())
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
        val published = liveHttp("POST", "/assessments/$id/publish", teacherCookie, publishBody, publishKey)
        assertEquals(200, published.statusCode(), published.body())
        val publishedBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(published.body())
        assertEquals("published", publishedBody.get("status").asText())
        assertTrue(publishedBody.hasNonNull("publishedAt"))
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
        val replayedStart = mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", startKey)
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() } }.andReturn()
        assertEquals(started.response.contentAsString, replayedStart.response.contentAsString)
        assertEquals(started.response.contentAsString, jdbc.queryForObject(
            "SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='startAttempt' AND idempotency_key=?",
            String::class.java, studentId, startKey,
        ))
        val attemptId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(started.response.contentAsString).get("id").asText()
        mvc.post("/api/v1/attempts") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "resume-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$assessmentId"}"""
        }.andExpect { status { isCreated() }; jsonPath("$.id") { value(attemptId) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM core.attempts WHERE assessment_id=? AND user_id=?", Int::class.java, assessmentId, studentId))
        val attempts = liveHttp("GET", "/attempts?assessmentId=$assessmentId", studentCookie)
        assertEquals(200, attempts.statusCode(), attempts.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(attempts.body()).get("items").any { it.get("id").asText() == attemptId })
        val questionId = jdbc.queryForObject("SELECT question_id FROM core.assessment_questions WHERE assessment_id=?", String::class.java, assessmentId)!!
        mvc.patch("/api/v1/attempts/$attemptId/answers") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"expectedRevision":1,"answers":{"$questionId":0}}"""
        }.andExpect { status { isOk() }; jsonPath("$.revision") { value(2) } }
        val revisedQuestion = """{"source":"authored","clientKey":"q","questionId":"$questionId","question":{"subject":"Math","topic":"Algebra","difficulty":"easy","institution":"AJA","year":2026,"statement":"Changed after start","options":["3","2"],"image":null,"correct":1,"explanation":"Changed"}}"""
        mvc.patch("/api/v1/assessments/$assessmentId") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "edit-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"questions":[$revisedQuestion]}"""
        }.andExpect { status { isConflict() } }
        val attemptRead = liveHttp("GET", "/attempts/$attemptId", studentCookie)
        assertEquals(200, attemptRead.statusCode(), attemptRead.body())
        val attemptReadBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(attemptRead.body())
        assertEquals(0, attemptReadBody.get("answers").get(questionId).asInt())
        assertEquals("1 + 1?", attemptReadBody.at("/questions/0/statement").asText())
        assertEquals("2", attemptReadBody.at("/questions/0/options/0").asText())
        assertFalse(attemptReadBody.at("/questions/0").has("correct"))
        assertFalse(attemptReadBody.at("/questions/0").has("explanation"))
        val confirmedEdit = liveHttp(
            "PATCH", "/assessments/$assessmentId", teacherCookie,
            """{"expectedRevision":2,"confirmResetDrafts":true,"questions":[$revisedQuestion]}""",
            "confirmed-edit-${UUID.randomUUID()}",
        )
        assertEquals(200, confirmedEdit.statusCode(), confirmedEdit.body())
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
        assertEquals(responses.first().response.contentAsString, responses.last().response.contentAsString)
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
        val submittedReplay = mvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", submitKey)
            cookie(Cookie("aja_session", studentCookie))
            content = body
        }.andExpect { status { isOk() }; jsonPath("$.receipt.id") { value(receiptId) } }.andReturn()
        assertEquals(responses.first().response.contentAsString, submittedReplay.response.contentAsString)
        assertEquals(submittedReplay.response.contentAsString, jdbc.queryForObject(
            "SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='submitAttempt' AND idempotency_key=?",
            String::class.java, studentId, submitKey,
        ))
        val replaySnapshot = tools.jackson.databind.json.JsonMapper.builder().build().readTree(submittedReplay.response.contentAsString).at("/questions/0")
        assertTrue(replaySnapshot.has("image") && replaySnapshot.get("image").isNull)
        assertTrue(replaySnapshot.has("correct"))
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
        val boundary = "B16Work-${UUID.randomUUID()}"
        val multipartBody = buildString {
            append("--$boundary\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\nwork\r\n")
            append("--$boundary\r\nContent-Disposition: form-data; name=\"parentId\"\r\n\r\n$assessmentId\r\n")
            append("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"work.txt\"\r\nContent-Type: text/plain\r\n\r\n")
            append(fileBytes.toString(Charsets.UTF_8))
            append("\r\n--$boundary--\r\n")
        }
        val upload = liveHttp(
            "POST", "/files", studentCookie, multipartBody, "upload-${UUID.randomUUID()}",
            contentType = "multipart/form-data; boundary=$boundary",
        )
        assertEquals(201, upload.statusCode(), upload.body())
        val fileId = tools.jackson.databind.json.JsonMapper.builder().build().readTree(upload.body()).get("id").asString()
        val otherAssessmentId = createB07Assessment(moduleId)
        mvc.post("/api/v1/submissions") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            header("Idempotency-Key", "wrong-parent-${UUID.randomUUID()}")
            cookie(Cookie("aja_session", studentCookie))
            content = """{"assessmentId":"$otherAssessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"work.txt"}]}"""
        }.andExpect { status { isConflict() } }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.submissions WHERE assessment_id=?", Int::class.java, otherAssessmentId))

        fun post(cookie: String, key: String) = liveHttp(
            "POST", "/submissions", cookie,
            """{"assessmentId":"$assessmentId","attachments":[{"kind":"file","fileId":"$fileId","name":"work.txt"},{"kind":"link","name":"Research","url":"https://example.test/work"}]}""",
            key,
        )

        val createKey = "submission-${UUID.randomUUID()}"
        val created = post(studentCookie, createKey)
        assertEquals(201, created.statusCode(), created.body())
        val parsed = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body())
        val submissionId = parsed.get("id").asString()
        assertEquals(assessmentId, parsed.get("assessmentId").asString())
        assertTrue(parsed.get("submittedAt").isNull)
        val nullAttachments = liveHttp("PATCH", "/submissions/$submissionId", studentCookie, """{"expectedRevision":1,"attachments":null}""")
        assertEquals(422, nullAttachments.statusCode(), nullAttachments.body())
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM core.submissions WHERE id=?", Int::class.java, submissionId))
        val replayedCreate = post(studentCookie, createKey)
        assertEquals(201, replayedCreate.statusCode(), replayedCreate.body())
        assertEquals(created.body(), replayedCreate.body())
        assertEquals(created.body(), jdbc.queryForObject("SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='createSubmission' AND target='/submissions' AND idempotency_key=?", String::class.java, studentId, createKey))
        assertEquals(submissionId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(replayedCreate.body()).get("id").asString())

        val submitKey = "submit-${UUID.randomUUID()}"
        val submitBody = """{"expectedRevision":1}"""
        fun submit(key: String) = liveHttp("POST", "/submissions/$submissionId/submit", studentCookie, submitBody, key)
        val submitResponse = submit(submitKey)
        assertEquals(200, submitResponse.statusCode(), submitResponse.body())
        val submitted = tools.jackson.databind.json.JsonMapper.builder().build().readTree(submitResponse.body())
        val receiptId = submitted.get("receipt").get("id").asString()
        assertTrue(receiptId.isNotBlank())
        val submitReplay = submit(submitKey)
        assertEquals(200, submitReplay.statusCode(), submitReplay.body())
        assertEquals(submitResponse.body(), submitReplay.body())
        assertEquals(submitResponse.body(), jdbc.queryForObject("SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='submitSubmission' AND target=? AND idempotency_key=?", String::class.java, studentId, "/submissions/$submissionId/submit", submitKey))
        assertEquals(receiptId, tools.jackson.databind.json.JsonMapper.builder().build().readTree(submitReplay.body()).get("receipt").get("id").asString())
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
        val liveSubmissionList = liveHttp("GET", "/submissions?page=1&pageSize=20&sort=id", teacherCookie)
        assertEquals(200, liveSubmissionList.statusCode())
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveSubmissionList.body()).get("total").asInt())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveSubmissionList.body()).get("items")[0].get("feedbackDraft").isNull)

        mvc.patch("/api/v1/submissions/$submissionId/feedback") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", teacherCookie))
            content = """{"expectedRevision":2,"comment":"Too high","grade":11}"""
        }.andExpect { status { isUnprocessableEntity() } }
        val savedFeedback = liveHttp(
            "PATCH", "/submissions/$submissionId/feedback", teacherCookie,
            """{"expectedRevision":2,"comment":"Good evidence","grade":0.000000125}""",
        )
        assertEquals(200, savedFeedback.statusCode(), savedFeedback.body())
        val savedFeedbackBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(savedFeedback.body())
        assertEquals(java.math.BigDecimal("0.000000125"), savedFeedbackBody.at("/feedbackDraft/grade").decimalValue())
        assertEquals(0, savedFeedbackBody.get("feedbackHistory").size())
        val studentRead = liveHttp("GET", "/submissions/$submissionId", studentCookie)
        assertEquals(200, studentRead.statusCode(), studentRead.body())
        val studentReadBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(studentRead.body())
        assertFalse(studentReadBody.has("feedbackDraft"))
        assertTrue(studentReadBody.get("feedbackPublished").isNull)

        val publishKey = "publish-${UUID.randomUUID()}"
        val publishedFeedback = liveHttp(
            "POST", "/submissions/$submissionId/publish-feedback", teacherCookie,
            """{"expectedRevision":3}""", publishKey,
        )
        assertEquals(200, publishedFeedback.statusCode(), publishedFeedback.body())
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(publishedFeedback.body()).get("feedbackHistory").size())
        val publishedReplay = liveHttp("POST", "/submissions/$submissionId/publish-feedback", teacherCookie, """{"expectedRevision":3}""", publishKey)
        assertEquals(publishedFeedback.body(), publishedReplay.body())
        assertEquals(publishedFeedback.body(), jdbc.queryForObject("SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='publishSubmissionFeedback' AND target=? AND idempotency_key=?", String::class.java, teacherId, "/submissions/$submissionId/publish-feedback", publishKey))
        val revisedDraft = liveHttp("PATCH", "/submissions/$submissionId/feedback", teacherCookie, """{"expectedRevision":4,"comment":"Revised note","grade":null}""")
        assertEquals(200, revisedDraft.statusCode(), revisedDraft.body())
        val revisedDraftBody = tools.jackson.databind.json.JsonMapper.builder().build().readTree(revisedDraft.body())
        assertTrue(revisedDraftBody.at("/feedbackPublished/grade").decimalValue() == java.math.BigDecimal("0.000000125"))
        assertEquals("Revised note", revisedDraftBody.at("/feedbackDraft/comment").asString())
        assertTrue(revisedDraftBody.get("feedbackUpdated").asBoolean())
        mvc.get("/api/v1/submissions/$submissionId") {
            header("Origin", "https://aja.example.test")
            cookie(Cookie("aja_session", studentCookie))
        }.andExpect { status { isOk() }; jsonPath("$.feedbackPublished.grade") { value(0.000000125) }; jsonPath("$.feedbackUpdated") { value(true) } }
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
    fun submissionUpdateAllowsOmittedAttachmentsAndRejectsNullOrCoercedRevisions() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val (classId, moduleId) = createB07WorkModule(adminCookie, teacherCookie, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val assessmentId = createB07Assessment(moduleId)
        val created = liveHttp(
            "POST", "/submissions", studentCookie,
            """{"assessmentId":"$assessmentId","attachments":[{"kind":"link","name":"Essay","url":"https://example.test/essay"}]}""",
            "optional-attachments-${UUID.randomUUID()}",
        )
        assertEquals(201, created.statusCode(), created.body())
        val id = tools.jackson.databind.json.JsonMapper.builder().build().readTree(created.body()).get("id").asString()

        val omitted = liveHttp("PATCH", "/submissions/$id", studentCookie, """{"expectedRevision":1}""")
        assertEquals(200, omitted.statusCode(), omitted.body())
        val afterOmitted = tools.jackson.databind.json.JsonMapper.builder().build().readTree(omitted.body())
        assertEquals(2, afterOmitted.get("revision").asInt())
        assertEquals("https://example.test/essay", afterOmitted.at("/attachments/0/url").asString())

        val explicitNull = liveHttp("PATCH", "/submissions/$id", studentCookie, """{"expectedRevision":2,"attachments":null}""")
        assertEquals(422, explicitNull.statusCode(), explicitNull.body())
        val stringRevision = liveHttp("PATCH", "/submissions/$id", studentCookie, """{"expectedRevision":"2"}""")
        assertEquals(422, stringRevision.statusCode(), stringRevision.body())
        assertEquals(2, jdbc.queryForObject("SELECT revision FROM core.submissions WHERE id=?", Int::class.java, id))
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
        val updated = liveHttp("PATCH", "/submissions/$id", studentCookie, """{"expectedRevision":1,"attachments":[{"kind":"link","name":"Final","url":"https://example.test/final"}]}""")
        assertEquals(200, updated.statusCode(), updated.body())
        assertEquals(2, tools.jackson.databind.json.JsonMapper.builder().build().readTree(updated.body()).get("revision").asInt())
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
}
