package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AttemptSubmitCommand
import br.com.ajadigital.core.assessment.application.models.SubmissionSubmitRecord
import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class SubmitSubmissionUseCase(private val store: SubmissionStoreOutputPort) : SubmitSubmissionInputPort {
    override fun submit(request: SubmitSubmissionCommand): br.com.ajadigital.core.assessment.domain.models.SubmissionView {
        val actor = request.actor
        val id = request.id
        val key = request.key
        val body = request.body
        SubmissionPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        SubmissionPolicy.id(id)
        SubmissionPolicy.validKey(key)
        require(body.fieldsValid)
        return store.submit(
            SubmissionSubmitRecord(
                actor,
                id,
                (body.expectedRevision ?: throw IllegalArgumentException()).also { require(it > 0) },
                key,
                request.requestHash,
            ),
        ) ?: throw AssessmentNotFound()
    }
}
