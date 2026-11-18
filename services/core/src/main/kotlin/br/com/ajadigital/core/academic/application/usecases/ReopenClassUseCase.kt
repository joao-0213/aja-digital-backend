package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto

import br.com.ajadigital.core.academic.application.models.AcademicStoreSetClassActiveDto

import br.com.ajadigital.core.academic.application.models.ReopenClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ReopenClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class ReopenClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReopenClassInputPort {
    override fun reopenClass(request: ReopenClassDto): Map<String, Any?> {
        val (actor, id, revision, key) = request
                return         transactionRunner.run(TransactionRunDto {
            AcademicIdempotency.execute(idempotency, objectMapper, AcademicAuthorization.requireAdmin(store, actor, true), "reopenClass", "/classes/$id/reopen", key, mapOf("expectedRevision" to revision)) {
                store.setClassActive(AcademicStoreSetClassActiveDto(id, actor.id, revision, true)) ?: AcademicPolicy.conflict(store.findClass(AcademicStoreFindClassDto(id, actor.id, "admin")))
            }
        })
    }
}
