package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest

internal object AttemptPolicy {
    fun student(actor: AuthenticatedActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AuthenticatedActor) {
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

    fun text(node: Any?, key: String) = ((node as? Map<*, *>)?.get(key) as? String) ?: throw IllegalArgumentException()

    fun integer(node: Any?, key: String) = when (val value = (node as? Map<*, *>)?.get(key)) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    } ?: throw IllegalArgumentException()

    fun integerValue(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    }

    fun fields(node: Any?) = (node as? Map<*, *>)?.keys?.filterIsInstance<String>()?.toSet() ?: throw IllegalArgumentException()
    fun isObject(node: Any?) = node is Map<*, *>
    fun value(node: Any?, key: String) = (node as? Map<*, *>)?.get(key)

    fun hash(mapper: JsonSerializerOutputPort, body: Any?) =
        MessageDigest.getInstance("SHA-256").digest(mapper.serialize(SerializeJsonDto(body))).joinToString("") { "%02x".format(it) }
}
