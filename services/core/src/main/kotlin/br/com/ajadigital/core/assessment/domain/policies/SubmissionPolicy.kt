package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.net.URI
import java.security.MessageDigest
import java.math.BigDecimal
import java.time.Instant

internal object SubmissionPolicy {
    fun isValidFeedbackGrade(grade: BigDecimal?, maxGrade: BigDecimal?): Boolean =
        grade == null || (maxGrade != null && grade <= maxGrade)

    fun canPublishFeedback(comment: String, grade: Any?): Boolean =
        !(comment.isBlank() && grade == null)

    fun areStagedFilesValid(fileIds: List<String>, lockedFileExpiries: Map<String, Instant>, now: Instant): Boolean =
        lockedFileExpiries.size == fileIds.size && fileIds.all { fileId -> lockedFileExpiries[fileId]?.isAfter(now) == true }

    fun attachments(value: Any?): List<SubmissionAttachment> {
        val node = value as? List<*> ?: throw IllegalArgumentException()
        require(node.size <= 5)
        val ids = mutableSetOf<String>()
        return node.map { rawItem ->
            val item = rawItem as? Map<*, *> ?: throw IllegalArgumentException()
            val name = text(item, "name").also { require(it.isNotEmpty()) }
            when (text(item, "kind")) {
                "file" -> {
                    require(fields(item) == setOf("kind", "fileId", "name"))
                    val fileId = text(item, "fileId").also(::id)
                    require(ids.add(fileId))
                    SubmissionAttachment("file", name, fileId = fileId)
                }
                "link" -> {
                    require(fields(item) == setOf("kind", "name", "url"))
                    val url = text(item, "url")
                    val uri = runCatching { URI(url) }.getOrNull() ?: throw IllegalArgumentException()
                    require(uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && uri.rawUserInfo == null && uri.host != null)
                    SubmissionAttachment("link", name, url = url)
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    fun student(actor: AuthenticatedActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
    }

    fun writer(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("teacher", "admin")) throw AcademicForbidden()
    }

    fun validKey(value: String) {
        require(value.length in 8..128)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun text(node: Any?, key: String) = ((node as? Map<*, *>)?.get(key) as? String) ?: throw IllegalArgumentException()

    fun integer(node: Any?, key: String) = when (val value = (node as? Map<*, *>)?.get(key)) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    } ?: throw IllegalArgumentException()

    fun fields(node: Any?) = (node as? Map<*, *>)?.keys?.filterIsInstance<String>()?.toSet() ?: throw IllegalArgumentException()
    fun isObject(node: Any?) = node is Map<*, *>
    fun value(node: Any?, key: String) = (node as? Map<*, *>)?.get(key)
    fun has(node: Any?, key: String) = (node as? Map<*, *>)?.containsKey(key) == true

    fun hash(mapper: JsonSerializerOutputPort, body: Any?) =
        MessageDigest.getInstance("SHA-256").digest(mapper.serialize(SerializeJsonDto(body))).joinToString("") { "%02x".format(it) }
}
