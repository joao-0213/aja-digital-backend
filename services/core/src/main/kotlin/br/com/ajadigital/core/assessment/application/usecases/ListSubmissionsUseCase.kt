package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreSubmissionsDto

import br.com.ajadigital.core.assessment.application.models.ListSubmissionsDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage
import br.com.ajadigital.core.assessment.application.ports.`in`.ListSubmissionsInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class ListSubmissionsUseCase(private val store: SubmissionStoreOutputPort) : ListSubmissionsInputPort {
    override fun submissions(request: ListSubmissionsDto): Map<String, Any?> {
                val (actor, page, pageSize, sort, assessmentId, studentId) = request
        SubmissionPolicy.reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(assessmentId == null || SubmissionPolicy.validId(assessmentId))
        require(studentId == null || SubmissionPolicy.validId(studentId))
        val found: SubmissionPage = store.submissions(SubmissionStoreSubmissionsDto(actor.id, actor.role, actor.sessionValidationReference, page, pageSize, sort, assessmentId, studentId))
        return mapOf("items" to found.items, "page" to found.page, "pageSize" to found.pageSize, "total" to found.total)
    }
}
