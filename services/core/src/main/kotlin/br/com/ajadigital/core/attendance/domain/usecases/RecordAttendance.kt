package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.domain.ports.`in`.RecordAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class RecordAttendance(
    private val store: AttendanceStore,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
    private val transactionRunner: TransactionRunner,
) : RecordAttendanceUseCase {
    override fun create(actor: AcademicActor, draft: AttendanceDraft, key: String): Map<String, Any?> = transactionRunner.run {
        AttendanceSupport.writable(actor)
        require(key.length in 8..128 && AttendanceSupport.validId(draft.subjectId) && draft.time.second == 0 && draft.time.nano == 0)
        require(draft.records.keys.all(AttendanceSupport::validId))
        val hash = AttendanceSupport.sha256(mapper.writeValueAsBytes(draft.copy(records = draft.records.toSortedMap())))
        store.authorizeCreate(actor.id, actor.role, actor.sessionTokenHash, draft.subjectId)
        try {
            idempotency.find(actor.id, "createAttendance", "/attendance", key, hash)?.let { return@run it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        store.create(actor.id, actor.role, actor.sessionTokenHash, draft).also {
            idempotency.save(actor.id, "createAttendance", "/attendance", key, hash, it)
        }
    }
}
