package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AttemptStoreStartDto

import br.com.ajadigital.core.assessment.application.models.StartAttemptDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.StartAttemptInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class StartAttemptUseCase(private val store: AttemptStoreOutputPort, private val mapper: JsonSerializerOutputPort) : StartAttemptInputPort {
    override fun start(request: StartAttemptDto): Map<String, Any?> {
                val (actor, key, body) = request
        AttemptPolicy.student(actor)
        AttemptPolicy.validKey(key)
        require(AttemptPolicy.isObject(body) && AttemptPolicy.fields(body) == setOf("assessmentId"))
        val assessmentId = AttemptPolicy.text(body, "assessmentId").also(AttemptPolicy::id)
        return store.start(AttemptStoreStartDto(actor.id, actor.role, actor.sessionValidationReference, assessmentId, key, AttemptPolicy.hash(mapper, body)))
    }
}
