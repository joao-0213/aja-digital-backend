package br.com.ajadigital.core.attendance

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.attendance.application.models.*
import br.com.ajadigital.core.attendance.application.ports.`in`.RecordAttendanceInputPort
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceReceiptStoreOutputPort
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.usecases.RecordAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.attendance.adapters.out.serialization.JacksonAttendanceRequestFingerprint
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class AttendanceApplicationTests {
    private val now = Instant.parse("2026-11-12T00:00:00Z")
    private val teacher = AuthenticatedActor("teacher", "teacher", true, now, "session")
    private val student = AuthenticatedActor("student", "student", true, now, "session")

    @Test
    fun `record command rejects a student and malformed record ids before calling persistence`() {
        val store = UnusedAttendanceStore()
        val receipts = object : AttendanceReceiptStoreOutputPort {
            override fun find(actorId: String, key: String, requestHash: String): AttendanceReceipt? = null
            override fun save(actorId: String, key: String, requestHash: String, receipt: AttendanceReceipt) = error("must not save")
        }
        val fingerprints = JacksonAttendanceRequestFingerprint(tools.jackson.databind.json.JsonMapper.builder().build())
        val runner = object : TransactionRunnerOutputPort { override fun <T> run(block: () -> T): T = block() }
        val port: RecordAttendanceInputPort = RecordAttendanceUseCase(store, receipts, fingerprints, runner)
        val valid = AttendanceDraft("subject", LocalDate.parse("2026-11-12"), LocalTime.NOON, emptyMap())
        assertThrows(AcademicForbidden::class.java) { port.create(RecordAttendanceCommand(student, valid, "attendance-key")) }
        assertThrows(IllegalArgumentException::class.java) {
            port.create(RecordAttendanceCommand(teacher, AttendanceDraft("subject", valid.date, valid.time, mapOf("bad id" to true)), "attendance-key"))
        }
        assertEquals(0, store.writes)
    }

    private class UnusedAttendanceStore : AttendanceStoreOutputPort {
        var writes = 0
        override fun authorizeCreate(request: AttendanceCreateAuthorizationCommand) { writes++ }
        override fun list(request: ListAttendanceSessionsRecordQuery): AttendanceSessionPageRecord = error("unused")
        override fun eligible(request: ListEligibleAttendanceStudentsRecordQuery): EligibleAttendanceStudentPageRecord = error("unused")
        override fun attendance(request: GetAttendanceSessionRecordQuery): AttendanceSessionRecord? = error("unused")
        override fun create(request: AttendanceRecordCommand): AttendanceSessionRecord = error("unused")
        override fun update(request: UpdateAttendanceRecordCommand): AttendanceSessionRecord? = error("unused")
    }
}
