package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateEnrollmentDto

import br.com.ajadigital.core.academic.application.models.CreateEnrollmentDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateEnrollmentUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateEnrollmentInputPort {
    override fun createEnrollment(request: CreateEnrollmentDto): Map<String, Any?> {
                val (actor, classId, emailInput, key) = request
        return         transactionRunner.run(TransactionRunDto {
            val admin = AcademicAuthorization.requireAdmin(store, actor, true)
            val email = emailInput.trim()
            require(EmailAddress.isValid(email))
            AcademicIdempotency.execute(idempotency, objectMapper, admin, "createEnrollment", "/enrollments", key, mapOf("classId" to classId, "email" to email)) {
                store.createEnrollment(AcademicStoreCreateEnrollmentDto(admin.id, classId, email))
            }
        })
    }
}
