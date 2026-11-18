package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentsDto

import br.com.ajadigital.core.assessment.application.models.ListAssessmentsDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class ListAssessmentsUseCase(private val store: AssessmentStoreOutputPort) : ListAssessmentsInputPort {
    override fun assessments(request: ListAssessmentsDto): Map<String, Any?> {
                val (actor, page, pageSize, sort, subjectId, moduleId, kind, status) = request
        AssessmentPolicy.reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || AssessmentPolicy.validId(subjectId))
        require(moduleId == null || AssessmentPolicy.validId(moduleId))
        require(kind == null || kind in setOf("quiz", "work"))
        require(status == null || status in setOf("draft", "published"))
        val result = store.assessments(AssessmentStoreAssessmentsDto(actor.id, actor.role, actor.sessionValidationReference, page, pageSize, sort, subjectId, moduleId, kind, status))
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
