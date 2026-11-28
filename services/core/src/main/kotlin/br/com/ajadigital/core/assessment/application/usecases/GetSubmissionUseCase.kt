package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.GetSubmissionQuery
import br.com.ajadigital.core.assessment.application.models.SubmissionGetQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.GetSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetSubmissionUseCase(private val store: SubmissionStoreOutputPort) : GetSubmissionInputPort {
    override fun submission(request: GetSubmissionQuery): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
        val (actor, id) = request
        SubmissionPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.id(id)
        return store.submission(SubmissionGetQuery(actor, id)) ?: throw AssessmentNotFound()
    }
}
