package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetAttempt(private val store: AttemptStore) : GetAttemptUseCase {
    override fun attempt(actor: AcademicActor, id: String): Map<String, Any?> {
        AttemptSupport.reader(actor)
        AttemptSupport.id(id)
        return store.attempt(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }
}
