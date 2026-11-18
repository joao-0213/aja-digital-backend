package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectModulesInputPort
import br.com.ajadigital.core.curriculum.application.models.ListSubjectModulesDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort

class ListSubjectModulesUseCase(
    private val store: SubjectStoreOutputPort,
) : ListSubjectModulesInputPort {
    override fun subjectModules(request: ListSubjectModulesDto): List<Map<String, Any?>> {
        val (actor, subjectId) = request
                CurriculumPolicy.requireReader(actor)
        return store.subjectModules(ListStoredSubjectModulesDto(subjectId, actor.id, actor.role, actor.sessionValidationReference))
    }
}
