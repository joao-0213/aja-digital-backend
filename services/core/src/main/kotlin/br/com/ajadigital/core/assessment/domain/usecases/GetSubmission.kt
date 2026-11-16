package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.GetSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetSubmission(private val store: SubmissionStore) : GetSubmissionUseCase {
    override fun submission(actor: AcademicActor, id: String): Map<String, Any?> {
        SubmissionSupport.reader(actor)
        SubmissionSupport.id(id)
        return store.submission(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }
}
