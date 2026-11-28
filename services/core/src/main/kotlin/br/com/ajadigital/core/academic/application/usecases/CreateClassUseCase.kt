package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateClassDto

import br.com.ajadigital.core.academic.application.models.CreateClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class CreateClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateClassInputPort {
    override fun createClass(request: CreateClassDto): AcademicClassView {
                val (actor, name, code, year, shift, key) = request
        return transactionRunner.run {
            val admin = AcademicAuthorization.requireAdmin(store, actor, true)
            require(name.isNotBlank() && code.isNotBlank() && year in 1900..2200 && shift in AcademicPolicy.shifts)
            AcademicIdempotency.execute(idempotency, admin, "createClass", "/classes", key, { fingerprints.createClass(name, code, year, shift) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeClass,
            ) {
                store.createClass(AcademicStoreCreateClassDto(admin.id, name.trim(), code.trim(), year, shift))
            }
        }
    }
}
