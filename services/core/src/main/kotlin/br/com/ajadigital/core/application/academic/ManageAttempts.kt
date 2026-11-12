package br.com.ajadigital.core.application.academic

import br.com.ajadigital.core.ports.out.AssessmentPage
import br.com.ajadigital.core.ports.out.AttemptStore
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

class InvalidAttemptAnswers : RuntimeException()

class ManageAttempts(private val store: AttemptStore, private val mapper: ObjectMapper) {
    fun start(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        student(actor)
        validKey(key)
        require(body.isObject && fields(body) == setOf("assessmentId"))
        val assessmentId = text(body, "assessmentId").also(::id)
        return store.start(actor.id, actor.role, actor.sessionTokenHash, assessmentId, key, hash(body))
    }

    fun attempts(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?> {
        reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(assessmentId == null || validId(assessmentId))
        require(studentId == null || validId(studentId))
        val found: AssessmentPage = store.attempts(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, assessmentId, studentId)
        return mapOf("items" to found.items, "page" to found.page, "pageSize" to found.pageSize, "total" to found.total)
    }

    fun attempt(actor: AcademicActor, id: String): Map<String, Any?> {
        reader(actor); id(id)
        return store.attempt(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }

    fun saveAnswers(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        student(actor); id(id)
        require(body.isObject && fields(body) == setOf("expectedRevision", "answers"))
        val revision = integer(body, "expectedRevision").also { require(it > 0) }
        val node = body.get("answers")
        require(node.isObject)
        val answers = node.properties().associate { (questionId, answer) ->
            id(questionId)
            questionId to when {
                answer.isNull -> null
                answer.isIntegralNumber && answer.canConvertToInt() && answer.intValue() >= 0 -> answer.intValue()
                else -> throw IllegalArgumentException()
            }
        }
        return store.saveAnswers(actor.id, actor.role, actor.sessionTokenHash, id, revision, answers) ?: throw AcademicNotFound()
    }

    fun submit(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        student(actor); id(id); validKey(key)
        require(body.isObject && fields(body) == setOf("expectedRevision"))
        val revision = integer(body, "expectedRevision").also { require(it > 0) }
        return store.submit(actor.id, actor.role, actor.sessionTokenHash, id, key, hash(body), revision) ?: throw AcademicNotFound()
    }

    fun result(actor: AcademicActor, id: String): Map<String, Any?> {
        reader(actor); id(id)
        return store.result(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }

    private fun student(actor: AcademicActor) { if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun reader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun validKey(value: String) { require(value.length in 8..128) }
    private fun id(value: String) { require(validId(value)) }
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun text(node: JsonNode, key: String) = node.get(key)?.takeIf { it.isTextual }?.asString() ?: throw IllegalArgumentException()
    private fun integer(node: JsonNode, key: String) = node.get(key)?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue() ?: throw IllegalArgumentException()
    private fun fields(node: JsonNode) = node.properties().map { it.key }.toSet()
    private fun hash(body: JsonNode) = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
}
