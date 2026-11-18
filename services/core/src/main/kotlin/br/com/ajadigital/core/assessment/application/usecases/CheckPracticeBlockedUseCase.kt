package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AttemptStorePracticeBlockedDto

import br.com.ajadigital.core.assessment.application.models.CheckPracticeBlockedDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CheckPracticeBlockedInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class CheckPracticeBlockedUseCase(private val store: AttemptStoreOutputPort) : CheckPracticeBlockedInputPort {
    override fun practiceBlocked(request: CheckPracticeBlockedDto): Boolean {
                val (actor, questionId) = request
        AttemptPolicy.id(questionId)
        return store.practiceBlocked(AttemptStorePracticeBlockedDto(actor.id, actor.role, actor.sessionValidationReference, questionId))
    }
}
