package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.PracticeBlockedQuery

import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedQuery

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CheckPracticeBlockedInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class CheckPracticeBlockedUseCase(private val store: AttemptStoreOutputPort) : CheckPracticeBlockedInputPort {
    override fun practiceBlocked(request: CheckPracticeBlockedQuery): Boolean {
                val (actor, questionId) = request
        AttemptPolicy.id(questionId)
        return store.practiceBlocked(PracticeBlockedQuery(actor.id, actor.role, actor.sessionValidationReference, questionId))
    }
}
