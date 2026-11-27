package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort

class ListAssessmentsUseCase(private val store: AssessmentReadOutputPort) : ListAssessmentsInputPort {
    override fun assessments(request: ListAssessmentsQuery): AssessmentResults {
        val (actor, page, pageSize, sort, subjectId, moduleId, kind, status) = request
        AssessmentPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || AssessmentPolicy.validId(subjectId))
        require(moduleId == null || AssessmentPolicy.validId(moduleId))
        require(kind == null || kind in setOf("quiz", "work"))
        require(status == null || status in setOf("draft", "published"))
        return store.assessments(ListAssessmentsQuery(
            actor, page, pageSize, sort, subjectId, moduleId, kind, status,
        ))
    }
}
