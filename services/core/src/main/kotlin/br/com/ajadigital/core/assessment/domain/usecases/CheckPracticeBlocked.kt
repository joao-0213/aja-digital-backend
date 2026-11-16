package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.CheckPracticeBlockedUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class CheckPracticeBlocked(private val store: AttemptStore) : CheckPracticeBlockedUseCase {
    override fun practiceBlocked(actor: AcademicActor, questionId: String): Boolean {
        AttemptSupport.id(questionId)
        return store.practiceBlocked(actor.id, actor.role, actor.sessionTokenHash, questionId)
    }
}
