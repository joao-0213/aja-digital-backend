package br.com.ajadigital.core.application.academic

import br.com.ajadigital.core.ports.out.ContentStore
import br.com.ajadigital.core.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.ports.out.IdempotencyStore
import br.com.ajadigital.core.ports.out.MaterialCreate
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.security.MessageDigest

class ManageContent(private val store: ContentStore, private val idempotency: IdempotencyStore, private val mapper: ObjectMapper) {
    fun upload(actor: AcademicActor, key: String, name: String, mime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?> {
        reader(actor)
        require(key.length in 8..128 && validId(parentId))
        require(name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name && name != "." && name != "..")
        require(purpose in setOf("material", "work", "forum"))
        require(bytes.size <= if (purpose == "forum") 5 * 1024 * 1024 else 10 * 1024 * 1024)
        when (purpose) {
            "material" -> require(bytes.isNotEmpty() && mime.equals("application/pdf", true) && name.endsWith(".pdf", true) && bytes.copyOfRange(0, minOf(5, bytes.size)).toString(Charsets.US_ASCII) == "%PDF-")
            "forum" -> require(validForumFile(name, mime, bytes))
        }
        return store.upload(actor.id, actor.role, actor.sessionTokenHash, key, name, normalizedMime(mime), purpose, parentId, bytes)
    }

    fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?> {
        require(validId(id) && validId(questionId) && name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name)
        val normalized = normalizedMime(mime)
        require(bytes.size in 1..(5 * 1024 * 1024) && normalized in setOf("image/png", "image/jpeg", "image/webp") && validForumFile(name, normalized, bytes))
        return store.provisionQuestionImage(id, questionId, name, normalized, bytes)
    }

    fun file(actor: AcademicActor, id: String) = store.file(id(id), actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
    fun download(actor: AcademicActor, id: String, disposition: String?) = store.download(id(id), actor.id, actor.role, actor.sessionTokenHash, disposition) ?: throw AcademicNotFound()
    fun discard(actor: AcademicActor, id: String) = store.discard(id(id), actor.id, actor.role, actor.sessionTokenHash)

    fun materials(actor: AcademicActor, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?) = run {
        reader(actor); require(page > 0 && size in 1..100 && sort in setOf("order", "-order"))
        require(moduleId == null || validId(moduleId)); require(subjectId == null || validId(subjectId))
        store.materials(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, moduleId, subjectId)
    }

    fun material(actor: AcademicActor, id: String) = store.material(id(id), actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()

    fun publish(actor: AcademicActor, key: String, moduleId: String, revision: Int, items: List<MaterialCreate>): Map<String, Any?> {
        writer(actor); require(key.length in 8..128 && validId(moduleId) && revision > 0 && items.size in 1..10)
        require(items.all { it.moduleId == moduleId && validId(it.moduleId) && validMaterial(it) })
        store.authorizeMaterialPublication(actor.id, actor.role, actor.sessionTokenHash, moduleId)
        val body = mapOf("moduleId" to moduleId, "expectedRevision" to revision, "items" to items)
        return idempotent(actor, "publishMaterials", "/materials/batch", key, body) {
            val result = store.publishMaterials(actor.id, actor.role, actor.sessionTokenHash, moduleId, revision, items) ?: throw AcademicConflict()
            page(result)
        }
    }

    fun update(actor: AcademicActor, id: String, revision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?> {
        writer(actor); require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (duration == null || duration in 1.0..600.0) && (content == null || content.length <= 1_000_000) && (videoUrl == null || validVideoUrl(videoUrl)))
        return store.updateMaterial(actor.id, actor.role, actor.sessionTokenHash, id(id), revision, title?.trim(), duration, content, videoUrl) ?: throw AcademicConflict()
    }

    fun reorder(actor: AcademicActor, moduleId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): Map<String, Any?> {
        writer(actor); require(validId(moduleId) && revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { validId(it.first) && it.second > 0 } && page > 0 && size in 1..100)
        return store.reorderMaterials(actor.id, actor.role, actor.sessionTokenHash, moduleId, revision, items, page, size)?.let(::page) ?: throw AcademicConflict()
    }

    fun progress(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?) = run {
        reader(actor); require(page > 0 && size in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || validId(subjectId)); require(materialId == null || validId(materialId))
        val result = store.progress(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, subjectId, materialId)
        mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    fun recordProgress(actor: AcademicActor, materialId: String, completed: Boolean?) : Map<String, Any?> {
        reader(actor); require(validId(materialId) && completed != false)
        return store.recordProgress(actor.id, actor.role, actor.sessionTokenHash, materialId, completed == true) ?: throw AcademicNotFound()
    }

    private fun validMaterial(item: MaterialCreate): Boolean = item.title.isNotBlank() && item.title.length <= 160 && item.duration in 1.0..600.0 && item.content.length <= 1_000_000 && when (item.kind) {
        "lesson" -> item.content.isNotBlank() && item.fileId == null && item.videoUrl == null
        "pdf" -> item.fileId?.let(::validId) == true && item.videoUrl == null
        "video" -> item.fileId == null && item.videoUrl?.let(::validVideoUrl) == true
        else -> false
    }

    private fun validVideoUrl(value: String): Boolean = runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    private fun validForumFile(name: String, mime: String, bytes: ByteArray): Boolean {
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
    private fun normalizedMime(value: String) = value.substringBefore(';').trim().lowercase()
    private fun reader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun writer(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden() }
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun id(value: String) = value.also { require(validId(it)) }
    private fun page(result: br.com.ajadigital.core.ports.out.AcademicPage) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun idempotent(actor: AcademicActor, operation: String, target: String, key: String, body: Any, command: () -> Map<String, Any?>): Map<String, Any?> {
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
        try { idempotency.find(actor.id, operation, target, key, hash)?.let { return it } } catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }
}
