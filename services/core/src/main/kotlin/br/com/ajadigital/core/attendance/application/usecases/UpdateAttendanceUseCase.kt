package br.com.ajadigital.core.attendance.application.usecases

import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceNotFound
import br.com.ajadigital.core.attendance.application.models.AttendanceRead
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceCommand
import br.com.ajadigital.core.attendance.application.ports.`in`.UpdateAttendanceInputPort
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceRecordCommand
import br.com.ajadigital.core.attendance.domain.policies.AttendancePolicy
import br.com.ajadigital.core.attendance.application.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class UpdateAttendanceUseCase(private val store: AttendanceStoreOutputPort, private val transactionRunner: TransactionRunnerOutputPort) : UpdateAttendanceInputPort {
    override fun update(command: UpdateAttendanceCommand): AttendanceRead = transactionRunner.run {
        AttendancePolicy.writable(command.actor.role, command.actor.active, command.actor.emailConfirmedAt != null)
        require(command.revision > 0 && command.records.keys.all(AttendancePolicy::validId))
        store.update(UpdateAttendanceRecordCommand(command.actor.id, command.actor.role, command.actor.sessionValidationReference, AttendancePolicy.checkedId(command.id), command.revision, command.records))
            ?.toAttendanceRead() ?: throw AttendanceNotFound()
    }
}
