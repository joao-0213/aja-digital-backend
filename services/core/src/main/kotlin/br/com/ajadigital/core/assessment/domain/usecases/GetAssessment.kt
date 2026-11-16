package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class GetAssessment(private val store: AssessmentStore) : GetAssessmentUseCase {
    override fun assessment(actor: AcademicActor, id: String): Map<String, Any?> {
        AssessmentSupport.reader(actor)
        AssessmentSupport.id(id)
        return store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }
}
