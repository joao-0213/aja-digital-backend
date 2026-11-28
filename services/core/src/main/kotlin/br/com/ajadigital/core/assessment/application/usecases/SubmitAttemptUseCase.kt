package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.AttemptSubmitCommand
import br.com.ajadigital.core.assessment.domain.models.AttemptResult

import br.com.ajadigital.core.assessment.application.models.SubmitAttemptCommand

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class SubmitAttemptUseCase(private val store: AttemptStoreOutputPort) : SubmitAttemptInputPort {
    override fun submit(request: SubmitAttemptCommand): AttemptResult {
                val (actor, id, key, body) = request
        AttemptPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        AttemptPolicy.id(id)
        AttemptPolicy.validKey(key)
        require(body != null && body.fieldsValid)
        val revision = body.expectedRevision?.also { require(it > 0) } ?: throw IllegalArgumentException()
        return store.submit(AttemptSubmitCommand(actor.id, actor.role, actor.sessionValidationReference, id, key, request.requestHash, revision)) ?: throw AssessmentNotFound()
    }
}
