package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto

import br.com.ajadigital.core.academic.application.models.AcademicStoreSetClassActiveDto

import br.com.ajadigital.core.academic.application.models.CloseClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CloseClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CloseClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CloseClassInputPort {
    override fun closeClass(request: CloseClassDto): AcademicClassView {
        val (actor, id, revision, key) = request
                return         transactionRunner.run(TransactionRunDto {
            AcademicIdempotency.execute(idempotency, objectMapper, AcademicAuthorization.requireAdmin(store, actor, true), "closeClass", "/classes/$id/close", key, mapOf("expectedRevision" to revision),
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeClass,
            ) {
                store.setClassActive(AcademicStoreSetClassActiveDto(id, actor.id, revision, false)) ?: AcademicPolicy.conflict(store.findClass(AcademicStoreFindClassDto(id, actor.id, "admin"))?.revision)
            }
        })
    }
}
