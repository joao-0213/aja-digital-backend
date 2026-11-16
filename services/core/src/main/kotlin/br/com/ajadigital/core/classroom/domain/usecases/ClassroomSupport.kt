package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object ClassroomSupport {
    val roles = setOf("student", "teacher", "admin")
    val shifts = setOf("Manhã", "Tarde", "Noite")
    val statuses = setOf("pending", "active", "ended", "cancelled")

    fun requireAdmin(store: AcademicStore, actor: AcademicActor, serializeAdminInvariant: Boolean = false): AcademicActor =
        actor.takeIf { it.active && it.role == "admin" && store.isCurrentAdminSession(it.id, it.sessionTokenHash, serializeAdminInvariant) }
            ?: throw AcademicForbidden()

    fun requireReader(actor: AcademicActor): AcademicActor =
        actor.takeIf { it.active && (it.role == "admin" || it.role == "teacher" || it.role == "student" && it.emailConfirmedAt != null) }
            ?: throw AcademicForbidden()

    fun requireStaffReader(actor: AcademicActor): AcademicActor =
        actor.takeIf { it.active && (it.role == "admin" || it.role == "teacher") } ?: throw AcademicForbidden()

    fun conflict(current: Map<String, Any?>?): Nothing =
        current?.let { throw AcademicConflict(it["revision"] as? Int) } ?: throw AcademicNotFound()

    fun checkedPage(page: Int) = page.also { require(it > 0) }
    fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }

    fun idempotent(
        idempotency: IdempotencyStore,
        objectMapper: ObjectMapper,
        actor: AcademicActor,
        operation: String,
        target: String,
        key: String,
        body: Any,
        command: () -> Map<String, Any?>,
    ): Map<String, Any?> {
        require(key.length in 8..128)
        val hash = sha256(objectMapper.writeValueAsBytes(body))
        try {
            idempotency.find(actor.id, operation, target, key, hash)?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }

    fun token() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
