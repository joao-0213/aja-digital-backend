package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentForbidden

internal object AttemptPolicy {
    fun student(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role != "student" || !emailConfirmed) throw AssessmentForbidden()
    }

    fun reader(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role !in setOf("student", "teacher", "admin") || role == "student" && !emailConfirmed) {
            throw AssessmentForbidden()
        }
    }

    fun validKey(value: String) {
        require(value.length in 8..128)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

}
