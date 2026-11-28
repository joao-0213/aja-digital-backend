package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.ListSubmissionsQuery
import br.com.ajadigital.core.assessment.application.models.SubmissionsListQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.ListSubmissionsInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage
import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ListSubmissionsUseCase(private val store: SubmissionStoreOutputPort) : ListSubmissionsInputPort {
    override fun submissions(request: ListSubmissionsQuery): SubmissionPage {
        val actor = request.actor
        val page = request.page
        val pageSize = request.pageSize
        val sort = request.sort
        val assessmentId = request.assessmentId
        val studentId = request.studentId
        SubmissionPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && pageSize in 1..MAX_PAGE_SIZE && sort in setOf("id", "-id"))
        require(assessmentId == null || SubmissionPolicy.validId(assessmentId))
        require(studentId == null || SubmissionPolicy.validId(studentId))
        val found: SubmissionPage = store.submissions(SubmissionsListQuery(actor, page, pageSize, sort, assessmentId, studentId))
        return found
    }
}

private const val MAX_PAGE_SIZE = 100
