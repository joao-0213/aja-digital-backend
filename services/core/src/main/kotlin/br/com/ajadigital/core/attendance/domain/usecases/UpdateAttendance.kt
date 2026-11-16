package br.com.ajadigital.core.attendance.domain.usecases

import br.com.ajadigital.core.attendance.domain.ports.`in`.UpdateAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateAttendance(
    private val store: AttendanceStore,
    private val transactionRunner: TransactionRunner,
) : UpdateAttendanceUseCase {
    override fun update(actor: AcademicActor, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?> = transactionRunner.run {
        AttendanceSupport.writable(actor)
        require(revision > 0 && records.keys.all(AttendanceSupport::validId))
        store.update(actor.id, actor.role, actor.sessionTokenHash, AttendanceSupport.checkedId(id), revision, records) ?: throw AcademicNotFound()
    }
}
