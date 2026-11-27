package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class CountPendingStudentAssessmentsUseCase(private val store: AssessmentReadOutputPort) : CountPendingStudentAssessmentsInputPort {
    override fun pendingStudentCount(actor: AuthenticatedActor): Int {
        AssessmentPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        if (actor.role != "student") throw Forbidden()
        return store.pendingStudentCount(actor)
    }
}
