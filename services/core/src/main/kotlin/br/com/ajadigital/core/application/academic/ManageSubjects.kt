package br.com.ajadigital.core.application.academic

import br.com.ajadigital.core.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.ports.out.IdempotencyStore
import br.com.ajadigital.core.ports.out.SubjectStore
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

class UnknownPublication : RuntimeException()
class InvalidAcademicReference : RuntimeException()

class ManageSubjects(private val store: SubjectStore, private val mapper: ObjectMapper, private val idempotency: IdempotencyStore) {
    fun subjects(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, query: String?) = run {
        requireReader(actor)
        store.subjects(actor.id, actor.role, actor.sessionTokenHash, checkedPage(page), checkedSize(size), checkedSort(sort), classId, query?.trim())
    }

    fun subject(actor: AcademicActor, id: String): Map<String, Any?> {
        requireReader(actor)
        return store.subject(id, actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
    }

    fun subjectModules(actor: AcademicActor, subjectId: String): List<Map<String, Any?>> {
        requireReader(actor)
        return store.subjectModules(subjectId, actor.id, actor.role, actor.sessionTokenHash)
    }

    fun createSubject(actor: AcademicActor, classId: String, name: String, teacherIds: List<String>, color: String, icon: String, description: String, key: String): Map<String, Any?> {
        requireAdmin(actor)
        store.authorize(actor.id, actor.role, actor.sessionTokenHash)
        return idempotent(actor, "createSubject", "/subjects", key, mapOf("classId" to classId, "name" to name, "teacherIds" to teacherIds, "color" to color, "icon" to icon, "description" to description)) {
            require(name.isNotBlank() && name.length <= 100 && description.length <= 10000 && color in colors && icon in icons)
            require(teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
            subjectCommand(store.createSubject(actor.id, actor.sessionTokenHash, classId, name.trim(), teacherIds, color, icon, description))
        }
    }

    fun updateSubject(actor: AcademicActor, id: String, revision: Int, classId: String?, name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?): Map<String, Any?> {
        requireAdmin(actor)
        require(revision > 0 && (name == null || name.isNotBlank() && name.length <= 100) && (description == null || description.length <= 10000) && (color == null || color in colors) && (icon == null || icon in icons))
        require(teacherIds == null || teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
        return subjectCommand(store.updateSubject(id, actor.id, actor.sessionTokenHash, revision, classId, name?.trim(), teacherIds, color, icon, description) ?: conflict(store.subject(id, actor.id, "admin", actor.sessionTokenHash)))
    }

    fun modules(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?) = run {
        requireReader(actor)
        store.modules(actor.id, actor.role, actor.sessionTokenHash, checkedPage(page), checkedSize(size), checkedModuleSort(sort), subjectId)
    }

    fun module(actor: AcademicActor, id: String): Map<String, Any?> {
        requireReader(actor)
        return store.module(id, actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
    }

    fun createModule(actor: AcademicActor, subjectId: String, title: String, description: String, key: String) =
        run {
            requireWriterRole(actor)
            store.authorize(actor.id, actor.role, actor.sessionTokenHash, subjectId)
            idempotent(actor, "createModule", "/modules", key, mapOf("subjectId" to subjectId, "title" to title, "description" to description)) {
            require(title.isNotBlank() && title.length <= 160 && description.length <= 10000)
            store.createModule(actor.id, actor.role, actor.sessionTokenHash, subjectId, title.trim(), description)
            }
        }

    fun updateModule(actor: AcademicActor, id: String, revision: Int, title: String?, description: String?): Map<String, Any?> {
        requireWriterRole(actor)
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (description == null || description.length <= 10000))
        return store.updateModule(id, actor.id, actor.role, actor.sessionTokenHash, revision, title?.trim(), description) ?: conflict(store.module(id, actor.id, actor.role, actor.sessionTokenHash))
    }

    fun reorder(actor: AcademicActor, subjectId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, pageSize: Int): Map<String, Any?> {
        requireWriterRole(actor)
        require(revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { it.second > 0 })
        val result = store.reorderModules(subjectId, actor.id, actor.role, actor.sessionTokenHash, revision, items, checkedPage(page), checkedSize(pageSize)) ?: conflict(store.subject(subjectId, actor.id, actor.role, actor.sessionTokenHash))
        return mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    fun visit(actor: AcademicActor, subjectId: String, publicationIds: List<String>? = null): Map<String, Any?> {
        requireReader(actor)
        require(publicationIds == null || publicationIds.distinct().size == publicationIds.size && publicationIds.all { it.length <= 139 && it.matches(Regex("^(material|notice|assessment):[A-Za-z0-9][A-Za-z0-9._:-]*$")) })
        return store.visit(subjectId, actor.id, actor.role, actor.sessionTokenHash, publicationIds) ?: throw AcademicNotFound()
    }

    private fun requireAdmin(actor: AcademicActor) { if (!actor.active || actor.role != "admin") throw AcademicForbidden() }
    private fun requireReader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }
    private fun requireWriterRole(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden() }
    private fun conflict(current: Map<String, Any?>?): Nothing = current?.let { throw AcademicConflict(it["revision"] as? Int) } ?: throw AcademicNotFound()
    private fun subjectCommand(subject: Map<String, Any?>) = subject.filterKeys { it in subjectSchema }
    private fun checkedPage(page: Int) = page.also { require(it > 0) }
    private fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    private fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }
    private fun checkedModuleSort(sort: String) = sort.also { require(it == "order" || it == "-order") }
    private fun idempotent(actor: AcademicActor, operation: String, target: String, key: String, body: Any, command: () -> Map<String, Any?>): Map<String, Any?> {
        require(key.length in 8..128)
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
        try { idempotency.find(actor.id, operation, target, key, hash)?.let { return it } }
        catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }

    companion object {
        private val subjectSchema = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "classId", "name", "teacherIds", "color", "icon", "description")
        private val colors = setOf("violet", "blue", "amber", "green")
        private val icons = setOf("book", "calculator", "atom", "flask", "leaf", "globe", "languages", "landmark", "palette", "graduation")
    }
}
