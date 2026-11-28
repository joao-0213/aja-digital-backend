package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.AttemptGetQuery
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord

import br.com.ajadigital.core.assessment.application.models.GetAttemptQuery

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetAttemptUseCase(private val store: AttemptStoreOutputPort) : GetAttemptInputPort {
    override fun attempt(request: GetAttemptQuery): AttemptRecord {
                val (actor, id) = request
        AttemptPolicy.reader(actor.role, actor.active, actor.emailConfirmedAt != null)
        AttemptPolicy.id(id)
        return store.attempt(AttemptGetQuery(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AssessmentNotFound()
    }
}
