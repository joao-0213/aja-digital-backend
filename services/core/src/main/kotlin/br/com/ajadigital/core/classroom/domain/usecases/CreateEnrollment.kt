package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateEnrollment(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateEnrollmentUseCase {
    override fun createEnrollment(actor: AcademicActor, classId: String, emailInput: String, key: String): Map<String, Any?> =
        transactionRunner.run {
            val admin = ClassroomSupport.requireAdmin(store, actor, true)
            val email = emailInput.trim()
            require(EmailAddress.isValid(email))
            ClassroomSupport.idempotent(idempotency, objectMapper, admin, "createEnrollment", "/enrollments", key, mapOf("classId" to classId, "email" to email)) {
                store.createEnrollment(admin.id, classId, email)
            }
        }
}
