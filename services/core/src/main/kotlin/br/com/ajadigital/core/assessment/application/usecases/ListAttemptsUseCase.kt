package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AttemptListQuery
import br.com.ajadigital.core.assessment.application.models.ListAttemptsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAttemptsInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.models.AttemptPage
import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ListAttemptsUseCase(private val store: AttemptStoreOutputPort) : ListAttemptsInputPort {
    override fun attempts(request: ListAttemptsQuery): AttemptPage {
        val actor = request.actor
        val page = request.page
        val pageSize = request.pageSize
        val sort = request.sort
        val assessmentId = request.assessmentId
        val studentId = request.studentId
        AttemptPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(page > 0 && pageSize in 1..MAX_PAGE_SIZE && sort in setOf("id", "-id"))
        require(assessmentId == null || AttemptPolicy.validId(assessmentId))
        require(studentId == null || AttemptPolicy.validId(studentId))
        return store.attempts(AttemptListQuery(actor.id, actor.role, actor.sessionValidationReference, page, pageSize, sort, assessmentId, studentId))
    }
}

private const val MAX_PAGE_SIZE = 100
