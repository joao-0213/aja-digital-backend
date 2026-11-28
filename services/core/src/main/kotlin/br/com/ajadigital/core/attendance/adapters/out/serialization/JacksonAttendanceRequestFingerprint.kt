package br.com.ajadigital.core.attendance.adapters.out.serialization

import br.com.ajadigital.core.attendance.application.ports.out.AttendanceRequestFingerprintOutputPort
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Component
class JacksonAttendanceRequestFingerprint(private val mapper: ObjectMapper) : AttendanceRequestFingerprintOutputPort {
    override fun create(draft: AttendanceDraft): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(draft.withRecords(draft.records.toSortedMap())))
        .joinToString("") { "%02x".format(it) }
}
