package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AttemptStoreResultDto

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptResultInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetAttemptResultUseCase(private val store: AttemptStoreOutputPort) : GetAttemptResultInputPort {
    override fun result(request: GetAttemptResultDto): Map<String, Any?> {
                val (actor, id) = request
        AttemptPolicy.reader(actor)
        AttemptPolicy.id(id)
        return store.result(AttemptStoreResultDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
    }
}
