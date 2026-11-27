package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.NotFound
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort

class GetAssessmentUseCase(private val store: AssessmentReadOutputPort) : GetAssessmentInputPort {
    override fun assessment(request: GetAssessmentQuery): AssessmentDetails {
        val (actor, id) = request
        AssessmentPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        AssessmentPolicy.id(id)
        return store.assessment(request)
            ?: throw NotFound()
    }
}
