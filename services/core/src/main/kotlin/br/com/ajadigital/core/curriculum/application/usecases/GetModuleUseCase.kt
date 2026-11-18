package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetModuleInputPort
import br.com.ajadigital.core.curriculum.application.models.GetModuleDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort

class GetModuleUseCase(
    private val store: SubjectStoreOutputPort,
) : GetModuleInputPort {
    override fun module(request: GetModuleDto): Map<String, Any?> {
        val (actor, id) = request
                CurriculumPolicy.requireReader(actor)
        return store.module(GetStoredModuleDto(id, actor.id, actor.role, actor.sessionValidationReference)) ?: throw AcademicNotFound()
    }
}
