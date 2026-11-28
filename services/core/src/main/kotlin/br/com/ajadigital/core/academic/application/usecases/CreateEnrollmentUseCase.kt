package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateEnrollmentDto

import br.com.ajadigital.core.academic.application.models.CreateEnrollmentDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class CreateEnrollmentUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateEnrollmentInputPort {
    override fun createEnrollment(request: CreateEnrollmentDto): AcademicEnrollmentView {
                val (actor, classId, emailInput, key) = request
        return transactionRunner.run {
            val admin = AcademicAuthorization.requireAdmin(store, actor, true)
            val email = emailInput.trim()
            require(EmailAddress.isValid(email))
            AcademicIdempotency.execute(idempotency, admin, "createEnrollment", "/enrollments", key, { fingerprints.createEnrollment(classId, email) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeEnrollment,
            ) {
                store.createEnrollment(AcademicStoreCreateEnrollmentDto(admin.id, classId, email))
            }
        }
    }
}
