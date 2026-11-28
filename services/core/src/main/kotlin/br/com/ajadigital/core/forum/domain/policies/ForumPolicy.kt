package br.com.ajadigital.core.forum.domain.policies

import br.com.ajadigital.core.forum.domain.exceptions.ForumForbidden

internal object ForumPolicy {
    fun readable(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role !in setOf("admin", "teacher", "student") || role == "student" && !emailConfirmed) throw ForumForbidden()
    }

    fun writable(role: String, active: Boolean, emailConfirmed: Boolean) = readable(role, active, emailConfirmed)

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

    fun checkedId(value: String) = value.also { require(validId(it)) }

}
