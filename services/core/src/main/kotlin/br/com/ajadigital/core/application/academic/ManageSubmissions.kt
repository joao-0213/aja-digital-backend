package br.com.ajadigital.core.application.academic

import br.com.ajadigital.core.ports.out.SubmissionAttachment
import br.com.ajadigital.core.ports.out.SubmissionPage
import br.com.ajadigital.core.ports.out.SubmissionStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.security.MessageDigest

class InvalidSubmissionFeedback : RuntimeException()

class ManageSubmissions(private val store: SubmissionStore, private val mapper: ObjectMapper) {
    fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        student(actor); validKey(key)
        require(body.isObject && fields(body) == setOf("assessmentId", "attachments"))
        val assessmentId = text(body, "assessmentId").also(::id)
        val attachments = attachments(body.get("attachments"))
        return store.create(actor.id, actor.role, actor.sessionTokenHash, assessmentId, attachments, key, hash(body))
    }

    fun submissions(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?> {
        reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(assessmentId == null || validId(assessmentId)); require(studentId == null || validId(studentId))
        val found: SubmissionPage = store.submissions(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, assessmentId, studentId)
        return mapOf("items" to found.items, "page" to found.page, "pageSize" to found.pageSize, "total" to found.total)
    }

    fun pendingFeedbackCount(actor: AcademicActor): Int {
        writer(actor)
        return store.pendingFeedbackCount(actor.id, actor.role, actor.sessionTokenHash)
    }

    fun submission(actor: AcademicActor, id: String): Map<String, Any?> {
        reader(actor); id(id)
        return store.submission(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }

    fun update(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        student(actor); id(id)
        require(body.isObject && fields(body).all { it in setOf("expectedRevision", "attachments") } && body.has("expectedRevision"))
        val revision = integer(body, "expectedRevision").also { require(it > 0) }
        val attachments = if (body.has("attachments")) attachments(body.get("attachments")) else null
        return store.update(actor.id, actor.role, actor.sessionTokenHash, id, revision, attachments) ?: throw AcademicNotFound()
    }

    fun submit(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        student(actor); id(id); validKey(key)
        require(body.isObject && fields(body) == setOf("expectedRevision"))
        return store.submit(actor.id, actor.role, actor.sessionTokenHash, id, integer(body, "expectedRevision").also { require(it > 0) }, key, hash(body)) ?: throw AcademicNotFound()
    }

    fun saveFeedback(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        writer(actor); id(id)
        require(body.isObject && fields(body) == setOf("expectedRevision", "comment", "grade"))
        val comment = text(body, "comment")
        val gradeNode = body.get("grade")
        val grade = when {
            gradeNode == null || gradeNode.isNull -> null
            gradeNode.isNumber -> gradeNode.decimalValue().also { require(it >= BigDecimal.ZERO) }
            else -> throw IllegalArgumentException()
        }
        return store.saveFeedback(actor.id, actor.role, actor.sessionTokenHash, id, integer(body, "expectedRevision").also { require(it > 0) }, comment, grade) ?: throw AcademicNotFound()
    }

    fun publishFeedback(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        writer(actor); id(id); validKey(key)
        require(body.isObject && fields(body) == setOf("expectedRevision"))
        return store.publishFeedback(actor.id, actor.role, actor.sessionTokenHash, id, integer(body, "expectedRevision").also { require(it > 0) }, key, hash(body)) ?: throw AcademicNotFound()
    }

    private fun attachments(node: JsonNode?): List<SubmissionAttachment> {
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

    private fun student(actor: AcademicActor) { if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun reader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun writer(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("teacher", "admin")) throw AcademicForbidden() }
    private fun validKey(value: String) { require(value.length in 8..128) }
    private fun id(value: String) { require(validId(value)) }
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun text(node: JsonNode, key: String) = node.get(key)?.takeIf { it.isTextual }?.asString() ?: throw IllegalArgumentException()
    private fun integer(node: JsonNode, key: String) = node.get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: throw IllegalArgumentException()
    private fun fields(node: JsonNode) = node.properties().map { it.key }.toSet()
    private fun hash(body: JsonNode) = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
}
