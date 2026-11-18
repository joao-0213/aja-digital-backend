package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.curriculum.application.models.ListSubjectsDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort

class ListSubjectsUseCase(
    private val store: SubjectStoreOutputPort,
) : ListSubjectsInputPort {
    override fun subjects(request: ListSubjectsDto): AcademicPage {
        val (actor, page, size, sort, classId, query) = request
                CurriculumPolicy.requireReader(actor)
        return store.subjects(ListSubjectsRecordDto(actor.id, actor.role, actor.sessionValidationReference, CurriculumPolicy.checkedPage(page), CurriculumPolicy.checkedSize(size), CurriculumPolicy.checkedSort(sort), classId, query?.trim()))
    }
}
