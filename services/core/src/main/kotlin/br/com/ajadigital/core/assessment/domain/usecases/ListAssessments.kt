package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.ListAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class ListAssessments(private val store: AssessmentStore) : ListAssessmentsUseCase {
    override fun assessments(actor: AcademicActor, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): Map<String, Any?> {
        AssessmentSupport.reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || AssessmentSupport.validId(subjectId))
        require(moduleId == null || AssessmentSupport.validId(moduleId))
        require(kind == null || kind in setOf("quiz", "work"))
        require(status == null || status in setOf("draft", "published"))
        val result = store.assessments(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, subjectId, moduleId, kind, status)
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
