package br.com.ajadigital.core.shared.adapters.out.serialization

import br.com.ajadigital.core.academic.adapters.out.serialization.JacksonAcademicRequestFingerprint
import br.com.ajadigital.core.announcements.adapters.out.serialization.JacksonAnnouncementRequestFingerprint
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.models.AnnouncementOccurrence
import br.com.ajadigital.core.attendance.adapters.out.serialization.JacksonAttendanceRequestFingerprint
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.forum.adapters.out.serialization.JacksonForumRequestFingerprint
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime

class RequestFingerprintCompatibilityTests {
    private val mapper: ObjectMapper = JsonMapper.builder().build()
    private fun legacyFingerprint(value: Any) = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value))
        .joinToString("") { "%02x".format(it) }

    @Test
    fun `feature fingerprints retain historical JSON inputs and field ordering`() {
        val attendance = AttendanceDraft("subject", LocalDate.parse("2026-11-12"), LocalTime.parse("14:00"), mapOf("z" to false, "a" to true))
        val sortedAttendance = attendance.withRecords(attendance.records.toSortedMap())
        assertEquals(legacyFingerprint(sortedAttendance), JacksonAttendanceRequestFingerprint(mapper).create(attendance))

        val topic = TopicDraft("class", "subject", "Title", "Body", listOf("tag"), "question", "q-1", null)
        assertEquals(legacyFingerprint(topic), JacksonForumRequestFingerprint(mapper).createTopic(topic))
        assertEquals(legacyFingerprint(mapOf("body" to "reply")), JacksonForumRequestFingerprint(mapper).reply("reply"))

        val announcement = AnnouncementDraft("class", "subject", "Title", "Body", false, AnnouncementOccurrence("2026-11-12", null, true))
        assertEquals(legacyFingerprint(announcement), JacksonAnnouncementRequestFingerprint(mapper).create(announcement))

        val academic = JacksonAcademicRequestFingerprint(mapper)
        assertEquals(legacyFingerprint(mapOf("name" to "N", "email" to "n@example.test", "role" to "student")), academic.createUser("N", "n@example.test", "student"))
        assertEquals(legacyFingerprint(mapOf("name" to " N ", "code" to " C ", "year" to 2026, "shift" to "Manhã")), academic.createClass(" N ", " C ", 2026, "Manhã"))
        assertEquals(legacyFingerprint(mapOf("classId" to "c", "email" to "n@example.test")), academic.createEnrollment("c", "n@example.test"))
        assertEquals(legacyFingerprint(mapOf("expectedRevision" to 3)), academic.expectedRevision(3))
    }
}
