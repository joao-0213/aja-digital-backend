package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto

import br.com.ajadigital.core.academic.application.models.AcademicStoreSetClassActiveDto

import br.com.ajadigital.core.academic.application.models.ReopenClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.ReopenClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class ReopenClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReopenClassInputPort {
    override fun reopenClass(request: ReopenClassDto): AcademicClassView {
        val (actor, id, revision, key) = request
                return transactionRunner.run {
            AcademicIdempotency.execute(idempotency, AcademicAuthorization.requireAdmin(store, actor, true), "reopenClass", "/classes/$id/reopen", key, { fingerprints.expectedRevision(revision) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeClass,
            ) {
                store.setClassActive(AcademicStoreSetClassActiveDto(id, actor.id, revision, true)) ?: AcademicPolicy.conflict(store.findClass(AcademicStoreFindClassDto(id, actor.id, "admin"))?.revision)
            }
        }
    }
}
