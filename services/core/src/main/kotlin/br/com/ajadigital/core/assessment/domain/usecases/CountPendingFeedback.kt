package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class CountPendingFeedback(private val store: SubmissionStore) : CountPendingFeedbackUseCase {
    override fun pendingFeedbackCount(actor: AcademicActor): Int {
        SubmissionSupport.writer(actor)
        return store.pendingFeedbackCount(actor.id, actor.role, actor.sessionTokenHash)
    }
}
