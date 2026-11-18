package br.com.ajadigital.core.attendance
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import javax.sql.DataSource
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.Instant
import br.com.ajadigital.core.attendance.adapters.out.persistence.JdbcAttendanceStore

class AttendanceIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun attendanceCanBeCreatedAndReplayedWithoutDuplicatingTheClassSession() {
        val (adminCookie, _) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (unassignedCookie, _) = userSession("teacher")
        val classId = createB06Class(adminCookie)
        val subjectId = createB06Subject(adminCookie, classId, teacherId)
        val (studentCookie, studentId) = userSession("student")
        createB07Enrollment(classId, studentId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
        val (peerCookie, peerId) = userSession("student")
        createB07Enrollment(classId, peerId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
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
        createB07Enrollment(classId, currentId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
        val (_, futureId) = userSession("student")
        createB07Enrollment(classId, futureId, teacherId, enrolledAt = java.time.Instant.parse("2026-11-13T03:00:00Z"))
        val (_, exitedId) = userSession("student")
        val exitedEnrollment = createB07Enrollment(classId, exitedId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
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
        createB07Enrollment(classId, studentId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
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
        createB07Enrollment(classId, studentId, teacherId, enrolledAt = Instant.parse("2026-11-12T00:00:00Z"))
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
        val page = store.list(br.com.ajadigital.core.attendance.domain.models.ListAttendanceDto(teacherId, "teacher", sha256(teacherCookie), 1, 10, "date", subjectId, null, null, null))
        assertEquals(10, page.items.size)
        assertEquals(12, page.total)
        assertTrue(queryCount.get() <= 5, "Expected at most five SQL statements for a page, observed ${queryCount.get()}")
    }
}
