package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.UpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.valueOrThrow
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.contracts.valueOrNull
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateModuleUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateModuleInputPort {
    override fun updateModule(request: UpdateModuleCommand): Module = transactionRunner.run {
        val (actor, id, revision, titleChange, descriptionChange) = request
        CurriculumPolicy.requireWriterRole(actor.active, actor.role)
        require(revision > 0)
        val title = titleChange.valueOrNull()
        val description = descriptionChange.valueOrNull()
        CurriculumPolicy.validateModuleFields(title, description)
        store.updateModule(StoreUpdateModuleCommand(id, actor.id, actor.role, actor.sessionValidationReference, revision, title?.trim(), description))
            .valueOrThrow()
    }
}
