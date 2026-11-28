package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceIdempotencyConflict
import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.RecordAttendanceCommand
import br.com.ajadigital.core.attendance.application.ports.`in`.RecordAttendanceInputPort
import br.com.ajadigital.core.attendance.domain.models.AttendanceReceipt
import br.com.ajadigital.core.attendance.application.models.AttendanceCreateAuthorizationCommand
import br.com.ajadigital.core.attendance.application.models.AttendanceRecordCommand
import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceReceiptStoreOutputPort
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceRequestFingerprintOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class RecordAttendanceUseCase(
    private val store: AttendanceStoreOutputPort,
    private val receipts: AttendanceReceiptStoreOutputPort,
    private val fingerprints: AttendanceRequestFingerprintOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : RecordAttendanceInputPort {
    override fun create(command: RecordAttendanceCommand): AttendanceRead = transactionRunner.run {
        AttendancePolicy.writable(command.actor.role, command.actor.active, command.actor.emailConfirmedAt != null)
        val draft = command.draft
        require(command.key.length in 8..128 && AttendancePolicy.validId(draft.subjectId) && draft.time.second == 0 && draft.time.nano == 0)
        require(draft.records.keys.all(AttendancePolicy::validId))
        val hash = fingerprints.create(draft)
        store.authorizeCreate(AttendanceCreateAuthorizationCommand(command.actor.id, command.actor.role, command.actor.sessionValidationReference, draft.subjectId))
        val replay = try { receipts.find(command.actor.id, command.key, hash) } catch (_: IdempotencyKeyReuse) { throw AttendanceIdempotencyConflict() }
        if (replay != null) return@run replay.toStaffView()
        val record = store.create(AttendanceRecordCommand(command.actor.id, command.actor.role, command.actor.sessionValidationReference, draft))
        val response = record.toAttendanceRead() as? br.com.ajadigital.core.attendance.application.models.AttendanceStaff
            ?: error("Attendance create must return the staff projection")
        receipts.save(command.actor.id, command.key, hash, AttendanceReceipt(response.id, response.revision, response.createdAt, response.updatedAt, response.updatedBy, response.subjectId, response.date, response.time, response.records))
        response
    }
}
