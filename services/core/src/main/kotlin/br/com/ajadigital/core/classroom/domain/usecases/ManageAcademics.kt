package br.com.ajadigital.core.classroom.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound

import br.com.ajadigital.core.identity.adapters.out.security.OutboxEncryption
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.classroom.domain.ports.out.ProvisionedIdentity
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID


class ManageAcademics(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val encryption: OutboxEncryption,
    private val passwordHasher: PasswordHasher,
    private val idempotency: IdempotencyStore,
) {
    fun users(actor: AcademicActor, page: Int, size: Int, sort: String, role: String?, active: Boolean?, q: String?) = run {
        requireAdmin(actor)
        store.listUsers(checkedPage(page), checkedSize(size), checkedSort(sort), role?.also { require(it in roles) }, active, q?.trim())
    }

    fun user(actor: AcademicActor, id: String) = requireAdmin(actor).let { store.findUser(id) ?: throw AcademicNotFound() }

    fun createUser(actor: AcademicActor, name: String, emailInput: String, role: String, key: String): Map<String, Any?> {
        requireAdmin(actor, true)
        val name = name.trim(); val email = emailInput.trim()
        require(name.isNotEmpty() && name.length <= 200 && EmailAddress.isValid(email) && role in setOf("teacher", "admin"))
        return idempotent(actor, "createUser", "/users", key, mapOf("name" to name, "email" to email, "role" to role)) {
        val now = Instant.now(clock); val id = UUID.randomUUID().toString()
        val passwordToken = token(); val confirmationToken = token()
        val passwordEvent = UUID.randomUUID().toString(); val confirmationEvent = UUID.randomUUID().toString()
        val passwordPayload = objectMapper.writeValueAsBytes(mapOf("recipient" to email, "token" to passwordToken, "userId" to id, "purpose" to "password_reset"))
        val confirmationPayload = objectMapper.writeValueAsBytes(mapOf("recipient" to email, "token" to confirmationToken, "userId" to id, "purpose" to "email_confirmation"))
        val passwordEncrypted = encryption.encrypt(passwordEvent, "identity.password-reset-requested", 1, passwordPayload)
        val confirmationEncrypted = encryption.encrypt(confirmationEvent, "identity.email-confirmation-requested", 1, confirmationPayload)
        store.createUser(ProvisionedIdentity(
            id, actor.id, name, email, role, now, passwordHasher.hash(token()), UUID.randomUUID().toString(), sha256(passwordToken), passwordEvent,
            passwordEncrypted.ciphertext, passwordEncrypted.nonce, UUID.randomUUID().toString(), sha256(confirmationToken),
            confirmationEvent, confirmationEncrypted.ciphertext, confirmationEncrypted.nonce, encryption.keyId,
        ))
        }
    }

    fun updateUser(actor: AcademicActor, id: String, revision: Int, name: String?, email: String?) =
        store.updateUser(id, requireAdmin(actor, true).id, revision.also { require(it > 0) }, name?.trim(), email?.trim()) ?: conflict(store.findUser(id))
    fun setRole(actor: AcademicActor, id: String, revision: Int, role: String) =
        store.changeRole(id, requireAdmin(actor, true).id, revision.also { require(it > 0) }, role.also { require(it in roles) }) ?: conflict(store.findUser(id))
    fun deactivate(actor: AcademicActor, id: String, revision: Int) =
        store.deactivateUser(id, requireAdmin(actor, true).id, revision.also { require(it > 0) }) ?: conflict(store.findUser(id))
    fun withdraw(actor: AcademicActor, id: String, revision: Int) =
        store.withdrawUser(id, requireAdmin(actor, true).id, revision.also { require(it > 0) }) ?: conflict(store.findUser(id))

    fun classes(actor: AcademicActor, page: Int, size: Int, sort: String, active: Boolean?, q: String?) = run {
        requireReader(actor)
        store.listClasses(actor.id, actor.role, checkedPage(page), checkedSize(size), checkedSort(sort), active, q?.trim())
    }
    fun dashboardClasses(actor: AcademicActor, pageSize: Int = 100) = requireReader(actor).let { store.dashboardClasses(actor.id, actor.role, pageSize) }
    fun classById(actor: AcademicActor, id: String) = requireReader(actor).let { store.findClass(id, actor.id, actor.role) ?: throw AcademicNotFound() }
    fun createClass(actor: AcademicActor, name: String, code: String, year: Int, shift: String, key: String): Map<String, Any?> {
        val admin = requireAdmin(actor, true); require(name.isNotBlank() && code.isNotBlank() && year in 1900..2200 && shift in shifts)
        return idempotent(admin, "createClass", "/classes", key, mapOf("name" to name, "code" to code, "year" to year, "shift" to shift)) { store.createClass(admin.id, name.trim(), code.trim(), year, shift) }
    }
    fun updateClass(actor: AcademicActor, id: String, revision: Int, name: String?, code: String?, year: Int?, shift: String?) =
        store.updateClass(id, requireAdmin(actor, true).id, revision.also { require(it > 0) }, name?.trim(), code?.trim(), year, shift) ?: conflict(store.findClass(id, actor.id, "admin"))
    fun closeClass(actor: AcademicActor, id: String, revision: Int, key: String) = idempotent(requireAdmin(actor, true), "closeClass", "/classes/$id/close", key, mapOf("expectedRevision" to revision)) { store.setClassActive(id, actor.id, revision, false) ?: conflict(store.findClass(id, actor.id, "admin")) }
    fun reopenClass(actor: AcademicActor, id: String, revision: Int, key: String) = idempotent(requireAdmin(actor, true), "reopenClass", "/classes/$id/reopen", key, mapOf("expectedRevision" to revision)) { store.setClassActive(id, actor.id, revision, true) ?: conflict(store.findClass(id, actor.id, "admin")) }

    fun enrollments(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, q: String?) = run {
        requireReader(actor)
        store.listEnrollments(actor.id, actor.role, checkedPage(page), checkedSize(size), checkedSort(sort), classId, studentId, status?.also { require(it.isNotEmpty() && it.size <= 4 && it.distinct().size == it.size && it.all { s -> s in statuses }) }, q?.trim())
    }
    fun enrollment(actor: AcademicActor, id: String) = requireAdmin(actor).let { store.findEnrollment(id) ?: throw AcademicNotFound() }
    fun preview(actor: AcademicActor, classId: String, email: String) = requireAdmin(actor).let { val value = email.trim(); require(EmailAddress.isValid(value)); store.enrollmentPreview(classId, value) ?: throw AcademicNotFound() }
    fun createEnrollment(actor: AcademicActor, classId: String, emailInput: String, key: String): Map<String, Any?> {
        val admin = requireAdmin(actor, true); val email = emailInput.trim(); require(EmailAddress.isValid(email))
        return idempotent(admin, "createEnrollment", "/enrollments", key, mapOf("classId" to classId, "email" to email)) { store.createEnrollment(admin.id, classId, email) }
    }
    fun transitionEnrollment(actor: AcademicActor, id: String, revision: Int, command: String, key: String) =
        idempotent(requireAdmin(actor, true), "${command}Enrollment", "/enrollments/$id/$command", key, mapOf("expectedRevision" to revision)) {
            store.transitionEnrollment(id, actor.id, revision.also { require(it > 0) }, command) ?: conflict(store.findEnrollment(id))
        }
    fun myEnrollments(actor: AcademicActor, page: Int, size: Int, sort: String): AcademicPage {
        if (actor.role != "student" || !actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myEnrollments(actor.id, checkedPage(page), checkedSize(size), checkedSort(sort))
    }

    fun myPendingEnrollmentCount(actor: AcademicActor): Int {
        if (actor.role != "student" || !actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myPendingEnrollmentCount(actor.id)
    }
    fun students(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, q: String?, history: Boolean) = run {
        requireStaffReader(actor)
        store.listStudents(actor.id, actor.role, checkedPage(page), checkedSize(size), checkedSort(sort), classId, q?.trim(), history)
    }
    fun student(actor: AcademicActor, id: String) = requireStaffReader(actor).let { store.findStudent(id, actor.id, actor.role) ?: throw AcademicNotFound() }

    private fun requireAdmin(actor: AcademicActor, serializeAdminInvariant: Boolean = false): AcademicActor = actor.takeIf { it.active && it.role == "admin" && store.isCurrentAdminSession(it.id, it.sessionTokenHash, serializeAdminInvariant) } ?: throw AcademicForbidden()
    private fun requireReader(actor: AcademicActor): AcademicActor = actor.takeIf { it.active && (it.role == "admin" || it.role == "teacher" || it.role == "student" && it.emailConfirmedAt != null) } ?: throw AcademicForbidden()
    private fun requireStaffReader(actor: AcademicActor): AcademicActor = actor.takeIf { it.active && (it.role == "admin" || it.role == "teacher") } ?: throw AcademicForbidden()
    private fun conflict(current: Map<String, Any?>?): Nothing = current?.let { throw AcademicConflict(it["revision"] as? Int) } ?: throw AcademicNotFound()
    private fun checkedPage(page: Int) = page.also { require(it > 0) }
    private fun checkedSize(size: Int) = size.also { require(it in 1..100) }
    private fun checkedSort(sort: String) = sort.also { require(it == "id" || it == "-id") }
    private fun idempotent(actor: AcademicActor, operation: String, target: String, key: String, body: Any, command: () -> Map<String, Any?>): Map<String, Any?> {
        require(key.length in 8..128)
        val hash = sha256(objectMapper.writeValueAsBytes(body))
        try { idempotency.find(actor.id, operation, target, key, hash)?.let { return it } }
        catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        return command().also { idempotency.save(actor.id, operation, target, key, hash, it) }
    }
    private fun token() = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    companion object { private val roles = setOf("student", "teacher", "admin"); private val shifts = setOf("Manhã", "Tarde", "Noite"); private val statuses = setOf("pending", "active", "ended", "cancelled") }
}
