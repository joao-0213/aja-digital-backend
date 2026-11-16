package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListAttemptsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class ListAttempts(private val store: AttemptStore) : ListAttemptsUseCase {
    override fun attempts(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?> {
        AttemptSupport.reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(assessmentId == null || AttemptSupport.validId(assessmentId))
        require(studentId == null || AttemptSupport.validId(studentId))
        val found: AssessmentPage = store.attempts(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, assessmentId, studentId)
        return mapOf("items" to found.items, "page" to found.page, "pageSize" to found.pageSize, "total" to found.total)
    }
}
