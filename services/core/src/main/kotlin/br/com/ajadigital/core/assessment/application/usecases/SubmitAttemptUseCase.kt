package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreSubmitDto

import br.com.ajadigital.core.assessment.domain.models.AttemptStoreSubmitDto

import br.com.ajadigital.core.assessment.application.models.SubmitAttemptDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitAttemptInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class SubmitAttemptUseCase(private val store: AttemptStoreOutputPort, private val mapper: JsonSerializerOutputPort) : SubmitAttemptInputPort {
    override fun submit(request: SubmitAttemptDto): Map<String, Any?> {
                val (actor, id, key, body) = request
        AttemptPolicy.student(actor)
        AttemptPolicy.id(id)
        AttemptPolicy.validKey(key)
        require(AttemptPolicy.isObject(body) && AttemptPolicy.fields(body) == setOf("expectedRevision"))
        val revision = AttemptPolicy.integer(body, "expectedRevision").also { require(it > 0) }
        return store.submit(AttemptStoreSubmitDto(actor.id, actor.role, actor.sessionValidationReference, id, key, AttemptPolicy.hash(mapper, body), revision)) ?: throw AcademicNotFound()
    }
}
