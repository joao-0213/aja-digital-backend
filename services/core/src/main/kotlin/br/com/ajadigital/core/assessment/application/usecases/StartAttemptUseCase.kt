package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AttemptStartCommand
import br.com.ajadigital.core.assessment.application.models.StartAttemptCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.StartAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class StartAttemptUseCase(private val store: AttemptStoreOutputPort) : StartAttemptInputPort {
    override fun start(request: StartAttemptCommand): AttemptRecord {
        val (actor, key, body) = request
        AttemptPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        AttemptPolicy.validKey(key)
        require(body != null && body.fieldsValid)
        val assessmentId = body.assessmentId?.also(AttemptPolicy::id) ?: throw IllegalArgumentException()
        return store.start(AttemptStartCommand(actor.id, actor.role, actor.sessionValidationReference, assessmentId, key, request.requestHash))
    }
}
