package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListModulesInputPort
import br.com.ajadigital.core.curriculum.application.models.ListModulesDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort

class ListModulesUseCase(
    private val store: SubjectStoreOutputPort,
) : ListModulesInputPort {
    override fun modules(request: ListModulesDto): AcademicPage {
        val (actor, page, size, sort, subjectId) = request
                CurriculumPolicy.requireReader(actor)
        return store.modules(ListStoredModulesDto(actor.id, actor.role, actor.sessionValidationReference, CurriculumPolicy.checkedPage(page), CurriculumPolicy.checkedSize(size), CurriculumPolicy.checkedModuleSort(sort), subjectId))
    }
}
