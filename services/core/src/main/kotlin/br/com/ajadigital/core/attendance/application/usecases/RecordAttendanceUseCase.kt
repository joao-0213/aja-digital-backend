package br.com.ajadigital.core.attendance.application.usecases
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.application.models.RecordAttendanceDto
import br.com.ajadigital.core.attendance.domain.models.AuthorizeAttendanceCreateDto
import br.com.ajadigital.core.attendance.domain.models.CreateAttendanceRecordDto
import br.com.ajadigital.core.attendance.application.ports.`in`.RecordAttendanceInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class RecordAttendanceUseCase(
    private val store: AttendanceStoreOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : RecordAttendanceInputPort {
    override fun create(request: RecordAttendanceDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, draft, key) = request
                AttendancePolicy.writable(actor)
        require(key.length in 8..128 && AttendancePolicy.validId(draft.subjectId) && draft.time.second == 0 && draft.time.nano == 0)
        require(draft.records.keys.all(AttendancePolicy::validId))
        val hash = AttendancePolicy.sha256(mapper.serialize(SerializeJsonDto(draft.copy(records = draft.records.toSortedMap()))))
        store.authorizeCreate(AuthorizeAttendanceCreateDto(actor.id, actor.role, actor.sessionValidationReference, draft.subjectId))
        try {
            idempotency.find(FindIdempotencyDto(actor.id, "createAttendance", "/attendance", key, hash))?.let { return@TransactionRunDto it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        store.create(CreateAttendanceRecordDto(actor.id, actor.role, actor.sessionValidationReference, draft)).also {
            idempotency.save(SaveIdempotencyDto(actor.id, "createAttendance", "/attendance", key, hash, it))
        }
    })
}
