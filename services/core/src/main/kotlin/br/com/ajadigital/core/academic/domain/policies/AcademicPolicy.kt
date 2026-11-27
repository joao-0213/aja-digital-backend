package br.com.ajadigital.core.academic.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound

internal object AcademicPolicy {
    val roles = setOf("student", "teacher", "admin")
    val shifts = setOf("Manhã", "Tarde", "Noite")
    val statuses = setOf("pending", "active", "ended", "cancelled")

    fun requireAdmin(role: String, active: Boolean) {
        if (!active || role != "admin") throw AcademicForbidden()
    }

    fun requireReader(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || !(role == "admin" || role == "teacher" || role == "student" && emailConfirmed)) throw AcademicForbidden()
    }

    fun requireStaffReader(role: String, active: Boolean) {
        if (!active || !(role == "admin" || role == "teacher")) throw AcademicForbidden()
    }

    fun conflict(revision: Int?): Nothing =
        revision?.let { throw AcademicConflict(it) } ?: throw AcademicNotFound()

    fun checkedPage(page: Int) = page.also { require(it > 0) }
    fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }

}
