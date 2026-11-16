package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

internal object AttemptSupport {
    fun student(actor: AcademicActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
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
