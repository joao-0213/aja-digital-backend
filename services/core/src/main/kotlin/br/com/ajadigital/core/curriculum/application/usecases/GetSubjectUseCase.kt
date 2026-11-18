package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetSubjectInputPort
import br.com.ajadigital.core.curriculum.application.models.GetSubjectDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort

class GetSubjectUseCase(
    private val store: SubjectStoreOutputPort,
) : GetSubjectInputPort {
    override fun subject(request: GetSubjectDto): Map<String, Any?> {
        val (actor, id) = request
                CurriculumPolicy.requireReader(actor)
        return store.subject(GetStoredSubjectDto(id, actor.id, actor.role, actor.sessionValidationReference)) ?: throw AcademicNotFound()
    }
}
