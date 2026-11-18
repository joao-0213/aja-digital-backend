package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.UpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.valueOrThrow
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.contracts.valueOrNull
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateSubjectInputPort {
    override fun updateSubject(request: UpdateSubjectCommand): Subject = transactionRunner.run {
        val actor = request.actor
        CurriculumPolicy.requireAdmin(actor.active, actor.role)
        require(request.revision > 0)
        val name = request.name.valueOrNull()
        val teacherIds = request.teacherIds.valueOrNull()
        val color = request.color.valueOrNull()
        val icon = request.icon.valueOrNull()
        val description = request.description.valueOrNull()
        CurriculumPolicy.validateSubjectFields(name, teacherIds, color, icon, description)
        store.updateSubject(StoreUpdateSubjectCommand(
            request.id, actor.id, actor.sessionValidationReference, request.revision,
            request.classId.valueOrNull(), name?.trim(), teacherIds, color, icon, description,
        )).valueOrThrow()
    }
}
