package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateSubjectInputPort
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

class CreateSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateSubjectInputPort {
    override fun createSubject(request: CreateSubjectDto): Map<String, Any?> {
        val (actor, classId, name, teacherIds, color, icon, description, key) = request
                return transactionRunner.run(TransactionRunDto {
            CurriculumPolicy.requireAdmin(actor)
            store.authorize(AuthorizeSubjectDto(actor.id, actor.role, actor.sessionValidationReference, null))
            idempotent(actor, "createSubject", "/subjects", key, mapOf("classId" to classId, "name" to name, "teacherIds" to teacherIds, "color" to color, "icon" to icon, "description" to description)) {
                require(name.isNotBlank() && name.length <= 100 && description.length <= 10000 && color in CurriculumPolicy.colors && icon in CurriculumPolicy.icons)
                require(teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
                CurriculumPolicy.subjectCommand(store.createSubject(CreateSubjectRecordDto(actor.id, actor.sessionValidationReference, classId, name.trim(), teacherIds, color, icon, description)))
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
