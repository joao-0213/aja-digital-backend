package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.AttemptResultQuery
import br.com.ajadigital.core.assessment.domain.models.AttemptResult

import br.com.ajadigital.core.assessment.application.models.GetAttemptResultQuery

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptResultInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetAttemptResultUseCase(private val store: AttemptStoreOutputPort) : GetAttemptResultInputPort {
    override fun result(request: GetAttemptResultQuery): AttemptResult {
                val (actor, id) = request
        AttemptPolicy.reader(actor)
        AttemptPolicy.id(id)
        return store.result(AttemptResultQuery(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
    }
}
