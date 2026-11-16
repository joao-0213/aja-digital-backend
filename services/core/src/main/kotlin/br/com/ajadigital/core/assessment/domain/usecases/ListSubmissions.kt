package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.models.SubmissionPage
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListSubmissionsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

class ListSubmissions(private val store: SubmissionStore) : ListSubmissionsUseCase {
    override fun submissions(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?> {
        SubmissionSupport.reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(assessmentId == null || SubmissionSupport.validId(assessmentId))
        require(studentId == null || SubmissionSupport.validId(studentId))
        val found: SubmissionPage = store.submissions(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, assessmentId, studentId)
        return mapOf("items" to found.items, "page" to found.page, "pageSize" to found.pageSize, "total" to found.total)
    }
}
