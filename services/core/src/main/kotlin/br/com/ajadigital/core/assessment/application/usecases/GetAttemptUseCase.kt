package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AttemptStoreAttemptDto

import br.com.ajadigital.core.assessment.application.models.GetAttemptDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetAttemptUseCase(private val store: AttemptStoreOutputPort) : GetAttemptInputPort {
    override fun attempt(request: GetAttemptDto): Map<String, Any?> {
                val (actor, id) = request
        AttemptPolicy.reader(actor)
        AttemptPolicy.id(id)
        return store.attempt(AttemptStoreAttemptDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
    }
}
