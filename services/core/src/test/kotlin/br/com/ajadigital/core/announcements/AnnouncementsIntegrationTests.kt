package br.com.ajadigital.core.announcements
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import java.sql.DriverManager
import java.time.ZoneId
import java.util.UUID

class AnnouncementsIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun announcementWithoutStoredTimeKeepsOmissionAndPatchValidation() {
        val (adminCookie, adminId) = adminSession()
        val (teacherCookie, teacherId) = userSession("teacher")
        val (studentCookie, studentId) = userSession("student")
        val classId = createB06Class(adminCookie)
        createB06Subject(adminCookie, classId, teacherId)
        createB07Enrollment(classId, studentId, teacherId)
        val id = "announcement-${UUID.randomUUID()}"
        val now = java.sql.Timestamp.from(mutableClock.instant())
        jdbc.update("""INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence)
            VALUES(?,1,?,?,?,?,?,?,?,'',false,?::jsonb)""", id, now, now, adminId, adminId, classId, null, "Legacy occurrence", """{"date":"2026-11-12","allDay":true}""")

        val detail = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/announcements/$id", studentCookie).body())
        assertFalse(detail.get("occurrence").has("time"))
        val calendar = tools.jackson.databind.json.JsonMapper.builder().build().readTree(liveHttp("GET", "/calendar?from=2026-11-12&to=2026-11-12", studentCookie).body())
        assertTrue(calendar.get("items").get(0).has("time"))
        assertTrue(calendar.get("items").get(0).get("time").isNull)
        assertEquals(422, liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":1}""").statusCode())
        val cleared = liveHttp("PATCH", "/announcements/$id", teacherCookie, """{"expectedRevision":1,"occurrence":null}""")
        assertEquals(200, cleared.statusCode(), cleared.body())
        assertTrue(tools.jackson.databind.json.JsonMapper.builder().build().readTree(cleared.body()).get("occurrence").isNull)
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
        val responseMapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val responseJson = responseMapper.readTree(response.body())
        val id = responseJson.get("id").asText()
        val expectedReceipt = linkedMapOf(
            "id" to id, "revision" to 1, "createdAt" to responseJson.get("createdAt").asText(),
            "updatedAt" to responseJson.get("updatedAt").asText(), "updatedBy" to teacherId,
            "classId" to classId, "subjectId" to subjectId, "title" to "Class update", "body" to "Tomorrow", "notify" to true,
            "occurrence" to linkedMapOf("date" to "2026-11-12", "time" to "10:30", "allDay" to false),
            "author" to linkedMapOf("id" to teacherId, "name" to responseJson.get("author").get("name").asText()),
        )
        assertEquals(responseMapper.writeValueAsString(expectedReceipt), response.body())

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
        assertEquals(response.body(), replay.body())
        assertEquals(response.body(), jdbc.queryForObject(
            "SELECT response_body FROM core.command_idempotency WHERE actor_id=? AND operation='createAnnouncement' AND idempotency_key=?",
            String::class.java, teacherId, createKey,
        ))
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
        val beforeRecipients = jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients", Int::class.java)!!
        val key = "rollback-${UUID.randomUUID()}"
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("create or replace function core.reject_announcement_outbox() returns trigger language plpgsql as \$\$ begin if new.event_type='academic.announcement-notification-requested' then raise exception 'forced announcement outbox failure'; end if; return new; end \$\$")
                statement.execute("create trigger reject_announcement_outbox before insert on core.outbox_events for each row execute function core.reject_announcement_outbox()")
            }
        }
        try {
            val response = liveHttp("POST", "/announcements", teacherCookie,
                """{"classId":"$classId","subjectId":null,"title":"Rollback","body":"No orphan","notify":true,"occurrence":null}""",
                key)
            assertEquals(500, response.statusCode(), response.body())
            assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM core.announcements", Int::class.java))
            assertEquals(beforeRecipients, jdbc.queryForObject("SELECT count(*) FROM core.announcement_notification_recipients", Int::class.java))
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM core.command_idempotency WHERE actor_id=? AND operation='createAnnouncement' AND idempotency_key=?", Int::class.java, teacherId, key))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists reject_announcement_outbox on core.outbox_events")
                    statement.execute("drop function if exists core.reject_announcement_outbox()")
                }
            }
        }
    }
}
