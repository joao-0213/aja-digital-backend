package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateClassDto

import br.com.ajadigital.core.academic.application.models.CreateClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CreateClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateClassInputPort {
    override fun createClass(request: CreateClassDto): Map<String, Any?> {
                val (actor, name, code, year, shift, key) = request
        return         transactionRunner.run(TransactionRunDto {
            val admin = AcademicAuthorization.requireAdmin(store, actor, true)
            require(name.isNotBlank() && code.isNotBlank() && year in 1900..2200 && shift in AcademicPolicy.shifts)
            AcademicIdempotency.execute(idempotency, objectMapper, admin, "createClass", "/classes", key, mapOf("name" to name, "code" to code, "year" to year, "shift" to shift)) {
                store.createClass(AcademicStoreCreateClassDto(admin.id, name.trim(), code.trim(), year, shift))
            }
        })
    }
}
