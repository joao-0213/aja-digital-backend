package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentDto

import br.com.ajadigital.core.assessment.application.models.GetAssessmentDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetAssessmentUseCase(private val store: AssessmentStoreOutputPort) : GetAssessmentInputPort {
    override fun assessment(request: GetAssessmentDto): Map<String, Any?> {
                val (actor, id) = request
        AssessmentPolicy.reader(actor)
        AssessmentPolicy.id(id)
        return store.assessment(AssessmentStoreAssessmentDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
    }
}
