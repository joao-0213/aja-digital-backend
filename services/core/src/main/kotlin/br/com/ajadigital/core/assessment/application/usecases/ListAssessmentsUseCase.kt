package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy

class ListAssessmentsUseCase(
    private val store: AssessmentReadOutputPort,
) : ListAssessmentsInputPort {
    override fun assessments(request: ListAssessmentsQuery): AssessmentResults {
        val actor = request.actor
        val page = request.page
        val pageSize = request.pageSize
        val sort = request.sort
        val subjectId = request.subjectId
        val moduleId = request.moduleId
        val kind = request.kind
        val status = request.status
        AssessmentPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && pageSize in 1..MAX_PAGE_SIZE && sort in setOf("id", "-id"))
        require(subjectId == null || AssessmentPolicy.validId(subjectId))
        require(moduleId == null || AssessmentPolicy.validId(moduleId))
        require(kind == null || kind in setOf("quiz", "work"))
        require(status == null || status in setOf("draft", "published"))
        return store.assessments(
            ListAssessmentsQuery(
                actor,
                page,
                pageSize,
                sort,
                subjectId,
                moduleId,
                kind,
                status,
            ),
        )
    }
}

private const val MAX_PAGE_SIZE = 100
