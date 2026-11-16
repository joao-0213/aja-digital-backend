package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.security.MessageDigest

internal object SubmissionSupport {
    fun attachments(node: JsonNode?): List<SubmissionAttachment> {
        require(node?.isArray == true && node.size() <= 5)
        val ids = mutableSetOf<String>()
        return List(node.size()) { index ->
            val item = node.get(index)
            require(item.isObject)
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

    fun student(actor: AcademicActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
    }

    fun writer(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("teacher", "admin")) throw AcademicForbidden()
    }

    fun validKey(value: String) {
        require(value.length in 8..128)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun text(node: JsonNode, key: String) = node.get(key)?.takeIf { it.isTextual }?.asString() ?: throw IllegalArgumentException()

    fun integer(node: JsonNode, key: String) =
        node.get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: throw IllegalArgumentException()

    fun fields(node: JsonNode) = node.properties().map { it.key }.toSet()

    fun hash(mapper: ObjectMapper, body: JsonNode) =
        MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
}
