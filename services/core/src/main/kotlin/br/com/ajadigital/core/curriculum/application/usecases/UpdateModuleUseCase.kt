package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateModuleInputPort
import br.com.ajadigital.core.curriculum.application.models.UpdateModuleDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateModuleUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateModuleInputPort {
    override fun updateModule(request: UpdateModuleDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision, title, description) = request
                CurriculumPolicy.requireWriterRole(actor)
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (description == null || description.length <= 10000))
        store.updateModule(UpdateModuleRecordDto(id, actor.id, actor.role, actor.sessionValidationReference, revision, title?.trim(), description)) ?: CurriculumPolicy.conflict(store.module(GetStoredModuleDto(id, actor.id, actor.role, actor.sessionValidationReference)))
    })
}
