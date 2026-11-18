package br.com.ajadigital.core.attendance.application.usecases
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.attendance.application.ports.`in`.UpdateAttendanceInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceDto
import br.com.ajadigital.core.attendance.domain.models.UpdateAttendanceRecordDto
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateAttendanceUseCase(
    private val store: AttendanceStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateAttendanceInputPort {
    override fun update(request: UpdateAttendanceDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision, records) = request
                AttendancePolicy.writable(actor)
        require(revision > 0 && records.keys.all(AttendancePolicy::validId))
        store.update(UpdateAttendanceRecordDto(actor.id, actor.role, actor.sessionValidationReference, AttendancePolicy.checkedId(id), revision, records)) ?: throw AcademicNotFound()
    })
}
