package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptResultUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetAttemptResult(private val store: AttemptStore) : GetAttemptResultUseCase {
    override fun result(actor: AcademicActor, id: String): Map<String, Any?> {
        AttemptSupport.reader(actor)
        AttemptSupport.id(id)
        return store.result(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }
}
