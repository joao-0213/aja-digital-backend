package br.com.ajadigital.core.curriculum.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

object CurriculumPolicy {
    val colors = setOf("violet", "blue", "amber", "green")
    val icons = setOf("book", "calculator", "atom", "flask", "leaf", "globe", "languages", "landmark", "palette", "graduation")
    private val subjectSchema = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "classId", "name", "teacherIds", "color", "icon", "description")

    fun requireAdmin(actor: AuthenticatedActor) {
        if (!actor.active || actor.role != "admin") throw AcademicForbidden()
    }

    fun requireReader(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun requireWriterRole(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden()
    }

    fun conflict(current: Map<String, Any?>?): Nothing = current?.let { throw AcademicConflict(it["revision"] as? Int) } ?: throw AcademicNotFound()

    fun subjectCommand(subject: Map<String, Any?>) = subject.filterKeys { it in subjectSchema }

    fun checkedPage(page: Int) = page.also { require(it > 0) }
    fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }
    fun checkedModuleSort(sort: String) = sort.also { require(it == "order" || it == "-order") }
}
