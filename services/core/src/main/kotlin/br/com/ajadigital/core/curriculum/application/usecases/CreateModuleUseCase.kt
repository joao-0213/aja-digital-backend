package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateModuleInputPort
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest

class CreateModuleUseCase(
    private val store: SubjectStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateModuleInputPort {
    override fun createModule(request: CreateModuleDto): Map<String, Any?> {
        val (actor, subjectId, title, description, key) = request
                return transactionRunner.run(TransactionRunDto {
            CurriculumPolicy.requireWriterRole(actor)
            store.authorize(AuthorizeSubjectDto(actor.id, actor.role, actor.sessionValidationReference, subjectId))
            idempotent(actor, "createModule", "/modules", key, mapOf("subjectId" to subjectId, "title" to title, "description" to description)) {
                require(title.isNotBlank() && title.length <= 160 && description.length <= 10000)
                store.createModule(CreateModuleRecordDto(actor.id, actor.role, actor.sessionValidationReference, subjectId, title.trim(), description))
            }
        })
    }

    private fun idempotent(actor: AuthenticatedActor, operation: String, target: String, key: String, body: Any, command: () -> Map<String, Any?>): Map<String, Any?> {
        require(key.length in 8..128)
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.serialize(SerializeJsonDto(body))).joinToString("") { "%02x".format(it) }
        try {
            idempotency.find(FindIdempotencyDto(actor.id, operation, target, key, hash))?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.save(SaveIdempotencyDto(actor.id, operation, target, key, hash, it)) }
    }
}
