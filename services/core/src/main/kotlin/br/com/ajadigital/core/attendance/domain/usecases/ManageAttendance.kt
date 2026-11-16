package br.com.ajadigital.core.attendance.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.domain.ports.`in`.ManageAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException


class ManageAttendance(
    private val store: AttendanceStore,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
    private val transactionRunner: TransactionRunner,
) : ManageAttendanceUseCase {
    override fun list(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: String?, to: String?) = run {
        readable(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        require(subjectId == null || validId(subjectId)); require(studentId == null || validId(studentId))
        val start = from?.let(::date); val end = to?.let(::date)
        require(start == null || end == null || !start.isAfter(end))
        store.list(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, subjectId, studentId, start, end)
    }

    override fun eligible(actor: AcademicActor, subjectId: String, date: String, time: String, page: Int, size: Int) = run {
        writable(actor)
        require(validId(subjectId) && page > 0 && size in 1..100 && time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")))
        store.eligible(actor.id, actor.role, actor.sessionTokenHash, subjectId, date(date), LocalTime.parse(time), page, size)
    }

    override fun attendance(actor: AcademicActor, id: String): Map<String, Any?> {
        readable(actor)
        return store.attendance(actor.id, actor.role, actor.sessionTokenHash, checkedId(id)) ?: throw AcademicNotFound()
    }

    override fun create(actor: AcademicActor, draft: AttendanceDraft, key: String): Map<String, Any?> = transactionRunner.run {
        writable(actor)
        require(key.length in 8..128 && validId(draft.subjectId) && draft.time.second == 0 && draft.time.nano == 0)
        require(draft.records.keys.all(::validId))
        val hash = sha256(mapper.writeValueAsBytes(draft.copy(records = draft.records.toSortedMap())))
        store.authorizeCreate(actor.id, actor.role, actor.sessionTokenHash, draft.subjectId)
        try { idempotency.find(actor.id, "createAttendance", "/attendance", key, hash)?.let { return@run it } }
        catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        store.create(actor.id, actor.role, actor.sessionTokenHash, draft).also {
            idempotency.save(actor.id, "createAttendance", "/attendance", key, hash, it)
        }
    }

    override fun update(actor: AcademicActor, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?> = transactionRunner.run {
        writable(actor)
        require(revision > 0 && records.keys.all(::validId))
        store.update(actor.id, actor.role, actor.sessionTokenHash, checkedId(id), revision, records) ?: throw AcademicNotFound()
    }

    private fun readable(actor: AcademicActor) { if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher", "student")) throw AcademicForbidden() }
    private fun writable(actor: AcademicActor) { if (!actor.active || actor.emailConfirmedAt == null || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden() }
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun checkedId(value: String) = value.also { require(validId(it)) }
    private fun date(value: String) = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException() }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
