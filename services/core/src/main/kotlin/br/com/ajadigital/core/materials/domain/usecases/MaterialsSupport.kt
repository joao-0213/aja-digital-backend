package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.materials.domain.models.MaterialCreate
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.security.MessageDigest

internal object MaterialsSupport {
    fun validMaterial(item: MaterialCreate): Boolean =
        item.title.isNotBlank() && item.title.length <= 160 && item.duration in 1.0..600.0 && item.content.length <= 1_000_000 && when (item.kind) {
            "lesson" -> item.content.isNotBlank() && item.fileId == null && item.videoUrl == null
            "pdf" -> item.fileId?.let(::validId) == true && item.videoUrl == null
            "video" -> item.fileId == null && item.videoUrl?.let(::validVideoUrl) == true
            else -> false
        }

    fun validVideoUrl(value: String): Boolean =
        runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)

    fun validForumFile(name: String, mime: String, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val header = bytes.take(12).toByteArray()
        return when (mime.lowercase()) {
            "application/pdf" -> name.endsWith(".pdf", true) && header.toString(Charsets.US_ASCII).startsWith("%PDF-")
            "image/png" -> name.endsWith(".png", true) && header.take(8).map { it.toInt() and 0xff } == listOf(137, 80, 78, 71, 13, 10, 26, 10)
            "image/jpeg" -> (name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)) && header.take(3).map { it.toInt() and 0xff } == listOf(255, 216, 255)
            "image/webp" -> name.endsWith(".webp", true) && header.size >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    fun normalizedMime(value: String) = value.substringBefore(';').trim().lowercase()

    fun reader(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
    }

    fun writer(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher")) {
            throw AcademicForbidden()
        }
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun id(value: String) = value.also { require(validId(it)) }

    fun page(result: AcademicPage) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)

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
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
        try {
            idempotency.find(actor.id, operation, target, key, hash)?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }
}
