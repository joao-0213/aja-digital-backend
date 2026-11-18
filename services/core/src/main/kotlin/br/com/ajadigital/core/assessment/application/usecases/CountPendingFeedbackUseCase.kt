package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStorePendingFeedbackCountDto

import br.com.ajadigital.core.assessment.application.models.CountPendingFeedbackDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingFeedbackInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class CountPendingFeedbackUseCase(private val store: SubmissionStoreOutputPort) : CountPendingFeedbackInputPort {
    override fun pendingFeedbackCount(request: CountPendingFeedbackDto): Int {
                val (actor) = request
        SubmissionPolicy.writer(actor)
        return store.pendingFeedbackCount(SubmissionStorePendingFeedbackCountDto(actor.id, actor.role, actor.sessionValidationReference))
    }
}
