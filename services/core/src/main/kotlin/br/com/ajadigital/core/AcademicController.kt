package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.academic.ManageAcademics
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = false)
data class UserCreateRequest(val name: String, val email: String, val role: String)
@JsonIgnoreProperties(ignoreUnknown = false)
data class UserEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val name: JsonNode = MissingNode.getInstance(), val email: JsonNode = MissingNode.getInstance())
@JsonIgnoreProperties(ignoreUnknown = false)
data class RoleRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val role: String)
@JsonIgnoreProperties(ignoreUnknown = false)
data class RevisionRequest(val expectedRevision: JsonNode = MissingNode.getInstance())
@JsonIgnoreProperties(ignoreUnknown = false)
data class ClassCreateRequest(val name: String, val code: String, val year: Int, val shift: String)
@JsonIgnoreProperties(ignoreUnknown = false)
data class ClassEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val name: JsonNode = MissingNode.getInstance(), val code: JsonNode = MissingNode.getInstance(), val year: JsonNode = MissingNode.getInstance(), val shift: JsonNode = MissingNode.getInstance())
@JsonIgnoreProperties(ignoreUnknown = false)
data class EnrollmentCreateRequest(val classId: String, val email: String)

@RestController
class AcademicController(private val auth: AuthenticateStudent, private val academics: ManageAcademics) {
    @GetMapping("/api/v1/users")
    fun users(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) role: String?, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = page(academics.users(actor(cookie), page, pageSize, sort, role, active, q))
    @PostMapping("/api/v1/users")
    @Transactional
    fun createUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: UserCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(academics.createUser(actor(cookie), body.name, body.email, body.role, idempotencyKey(key)))
    @GetMapping("/api/v1/users/{id}")
    fun user(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = academics.user(actor(cookie), id(id))
    @PatchMapping("/api/v1/users/{id}")
    @Transactional
    fun updateUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: UserEditRequest) = academics.updateUser(actor(cookie), id(id), revision(body.expectedRevision), text(body.name), text(body.email))
    @PostMapping("/api/v1/users/{id}/role")
    @Transactional
    fun role(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RoleRequest) = academics.setRole(actor(cookie), id(id), revision(body.expectedRevision), body.role)
    @PostMapping("/api/v1/users/{id}/deactivate")
    @Transactional
    fun deactivate(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = academics.deactivate(actor(cookie), id(id), revision(body.expectedRevision))
    @PostMapping("/api/v1/users/{id}/withdraw")
    @Transactional
    fun withdraw(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = academics.withdraw(actor(cookie), id(id), revision(body.expectedRevision))

    @GetMapping("/api/v1/classes")
    fun classes(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = page(academics.classes(actor(cookie), page, pageSize, sort, active, q))
    @PostMapping("/api/v1/classes")
    @Transactional
    fun createClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ClassCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(academics.createClass(actor(cookie), body.name, body.code, body.year, body.shift, idempotencyKey(key)))
    @GetMapping("/api/v1/classes/{id}")
    fun classById(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = academics.classById(actor(cookie), id(id))
    @PatchMapping("/api/v1/classes/{id}")
    @Transactional
    fun updateClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ClassEditRequest) = academics.updateClass(actor(cookie), id(id), revision(body.expectedRevision), text(body.name), text(body.code), integer(body.year), text(body.shift))
    @PostMapping("/api/v1/classes/{id}/close")
    @Transactional
    fun closeClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = academics.closeClass(actor(cookie), id(id), revision(body.expectedRevision), idempotencyKey(key))
    @PostMapping("/api/v1/classes/{id}/reopen")
    @Transactional
    fun reopenClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = academics.reopenClass(actor(cookie), id(id), revision(body.expectedRevision), idempotencyKey(key))

    @GetMapping("/api/v1/enrollments")
    fun enrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) studentId: String?, @RequestParam(required = false) status: List<String>?, @RequestParam(required = false) q: String?) = page(academics.enrollments(actor(cookie), page, pageSize, sort, classId, studentId, status, q))
    @PostMapping("/api/v1/enrollments")
    @Transactional
    fun createEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: EnrollmentCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(academics.createEnrollment(actor(cookie), id(body.classId), body.email, idempotencyKey(key)))
    @GetMapping("/api/v1/enrollments/preview")
    fun preview(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam classId: String, @RequestParam email: String) = academics.preview(actor(cookie), id(classId), email)
    @GetMapping("/api/v1/enrollments/{id}")
    fun enrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = academics.enrollment(actor(cookie), id(id))
    @PostMapping("/api/v1/enrollments/{id}/activate")
    @Transactional
    fun activateEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = academics.transitionEnrollment(actor(cookie), id(id), revision(body.expectedRevision), "activate", idempotencyKey(key))
    @PostMapping("/api/v1/enrollments/{id}/cancel")
    @Transactional
    fun cancelEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = academics.transitionEnrollment(actor(cookie), id(id), revision(body.expectedRevision), "cancel", idempotencyKey(key))
    @PostMapping("/api/v1/enrollments/{id}/end")
    @Transactional
    fun endEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = academics.transitionEnrollment(actor(cookie), id(id), revision(body.expectedRevision), "end", idempotencyKey(key))
    @GetMapping("/api/v1/me/enrollments")
    fun myEnrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String) = page(academics.myEnrollments(actor(cookie), page, pageSize, sort))
    @GetMapping("/api/v1/students")
    fun students(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?, @RequestParam(defaultValue = "false") history: Boolean) = page(academics.students(actor(cookie), page, pageSize, sort, classId, q, history))
    @GetMapping("/api/v1/students/{id}")
    fun student(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = academics.student(actor(cookie), id(id))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
    private fun idempotencyKey(value: String?): String = value?.also { require(it.length in 8..128) } ?: throw IllegalArgumentException()
    private fun id(value: String) = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
    private fun text(value: JsonNode) = if (value.isMissingNode) null else value.asText().also { require(value.isTextual) }
    private fun integer(value: JsonNode) = if (value.isMissingNode) null else value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
    private fun revision(value: JsonNode) = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt() && it > 0) }
    private fun page(result: br.com.ajadigital.core.ports.out.AcademicPage) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
}

@RestControllerAdvice
class AcademicExceptionHandler {
    @ExceptionHandler(AcademicForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "This action is not allowed"))
    @ExceptionHandler(AcademicNotFound::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(AcademicConflict::class)
    fun conflict(exception: AcademicConflict): ResponseEntity<Any> {
        val body = mutableMapOf<String, Any?>("code" to "REVISION_CONFLICT", "message" to "Resource changed or conflicts with current state", "requestId" to UUID.randomUUID().toString())
        exception.currentRevision?.let { body["details"] = mapOf("currentRevision" to it) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to body))
    }
    @ExceptionHandler(AcademicIdempotencyConflict::class)
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))
    @ExceptionHandler(DataIntegrityViolationException::class, IllegalStateException::class)
    fun stateConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("BUSINESS_CONFLICT", "Resource conflicts with current state"))
    @ExceptionHandler(NoSuchElementException::class)
    fun missing() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))
    private fun error(code: String, message: String) = mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()))
}
