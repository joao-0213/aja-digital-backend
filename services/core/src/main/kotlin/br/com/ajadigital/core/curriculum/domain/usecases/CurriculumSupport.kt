package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

internal object CurriculumSupport {
    val subjectSchema = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "classId", "name", "teacherIds", "color", "icon", "description")
    val colors = setOf("violet", "blue", "amber", "green")
    val icons = setOf("book", "calculator", "atom", "flask", "leaf", "globe", "languages", "landmark", "palette", "graduation")

    fun requireAdmin(actor: AcademicActor) {
        if (!actor.active || actor.role != "admin") throw AcademicForbidden()
    }

    fun requireReader(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun requireWriterRole(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden()
    }

    fun conflict(current: Map<String, Any?>?): Nothing = current?.let { throw AcademicConflict(it["revision"] as? Int) } ?: throw AcademicNotFound()

    fun subjectCommand(subject: Map<String, Any?>) = subject.filterKeys { it in subjectSchema }

    fun checkedPage(page: Int) = page.also { require(it > 0) }

    fun checkedSize(size: Int) = size.also { require(it in 1..100) }

    fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }

    fun checkedModuleSort(sort: String) = sort.also { require(it == "order" || it == "-order") }

    fun idempotent(
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        actor: AcademicActor,
        operation: String,
        target: String,
        key: String,
        body: Any,
        command: () -> Map<String, Any?>,
    ): Map<String, Any?> {
        require(key.length in 8..128)
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
        try {
            idempotency.find(actor.id, operation, target, key, hash)?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }
}
