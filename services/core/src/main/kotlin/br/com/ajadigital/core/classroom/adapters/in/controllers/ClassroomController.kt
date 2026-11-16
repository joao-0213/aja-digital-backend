package br.com.ajadigital.core.classroom.adapters.`in`.controllers

import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.ClassCreateRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.ClassEditRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.EnrollmentCreateRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.RevisionRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.RoleRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.UserCreateRequest
import br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests.UserEditRequest
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CloseClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.DeactivateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetStudentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListUsersUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.PreviewEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ReopenClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.SetUserRoleUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.TransitionEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.WithdrawUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestId
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestIdempotencyKey
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestRevision
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestText
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
class ClassroomController(
    private val auth: AuthenticateStudentUseCase,
    private val listUsers: ListUsersUseCase,
    private val getUser: GetUserUseCase,
    private val createUser: CreateUserUseCase,
    private val updateUser: UpdateUserUseCase,
    private val setUserRole: SetUserRoleUseCase,
    private val deactivateUser: DeactivateUserUseCase,
    private val withdrawUser: WithdrawUserUseCase,
    private val listClasses: ListClassesUseCase,
    private val getClass: GetClassUseCase,
    private val createClass: CreateClassUseCase,
    private val updateClass: UpdateClassUseCase,
    private val closeClass: CloseClassUseCase,
    private val reopenClass: ReopenClassUseCase,
    private val listEnrollments: ListEnrollmentsUseCase,
    private val getEnrollment: GetEnrollmentUseCase,
    private val previewEnrollment: PreviewEnrollmentUseCase,
    private val createEnrollment: CreateEnrollmentUseCase,
    private val transitionEnrollment: TransitionEnrollmentUseCase,
    private val listMyEnrollments: ListMyEnrollmentsUseCase,
    private val listStudents: ListStudentsUseCase,
    private val getStudent: GetStudentUseCase,
) {
    @GetMapping("/api/v1/users")
    fun users(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) role: String?, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = pagedBody(listUsers.users(actor(cookie), page, pageSize, sort, role, active, q))
    @PostMapping("/api/v1/users")
    fun createUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: UserCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createUser.createUser(actor(cookie), body.name, body.email, body.role, requestIdempotencyKey(key)))
    @GetMapping("/api/v1/users/{id}")
    fun user(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getUser.user(actor(cookie), requestId(id))
    @PatchMapping("/api/v1/users/{id}")
    fun updateUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: UserEditRequest) = updateUser.updateUser(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.name), requestText(body.email))
    @PostMapping("/api/v1/users/{id}/role")
    fun role(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RoleRequest) = setUserRole.setRole(actor(cookie), requestId(id), requestRevision(body.expectedRevision), body.role)
    @PostMapping("/api/v1/users/{id}/deactivate")
    fun deactivate(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = deactivateUser.deactivate(actor(cookie), requestId(id), requestRevision(body.expectedRevision))
    @PostMapping("/api/v1/users/{id}/withdraw")
    fun withdraw(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = withdrawUser.withdraw(actor(cookie), requestId(id), requestRevision(body.expectedRevision))

    @GetMapping("/api/v1/classes")
    fun classes(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = pagedBody(listClasses.classes(actor(cookie), page, pageSize, sort, active, q))
    @PostMapping("/api/v1/classes")
    fun createClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ClassCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createClass.createClass(actor(cookie), body.name, body.code, body.year, body.shift, requestIdempotencyKey(key)))
    @GetMapping("/api/v1/classes/{id}")
    fun classById(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getClass.classById(actor(cookie), requestId(id))
    @PatchMapping("/api/v1/classes/{id}")
    fun updateClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ClassEditRequest) = updateClass.updateClass(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.name), requestText(body.code), requestInteger(body.year), requestText(body.shift))
    @PostMapping("/api/v1/classes/{id}/close")
    fun closeClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = closeClass.closeClass(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestIdempotencyKey(key))
    @PostMapping("/api/v1/classes/{id}/reopen")
    fun reopenClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = reopenClass.reopenClass(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestIdempotencyKey(key))

    @GetMapping("/api/v1/enrollments")
    fun enrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) studentId: String?, @RequestParam(required = false) status: List<String>?, @RequestParam(required = false) q: String?) = pagedBody(listEnrollments.enrollments(actor(cookie), page, pageSize, sort, classId, studentId, status, q))
    @PostMapping("/api/v1/enrollments")
    fun createEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: EnrollmentCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createEnrollment.createEnrollment(actor(cookie), requestId(body.classId), body.email, requestIdempotencyKey(key)))
    @GetMapping("/api/v1/enrollments/preview")
    fun preview(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam classId: String, @RequestParam email: String) = previewEnrollment.preview(actor(cookie), requestId(classId), email)
    @GetMapping("/api/v1/enrollments/{id}")
    fun enrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getEnrollment.enrollment(actor(cookie), requestId(id))
    @PostMapping("/api/v1/enrollments/{id}/activate")
    fun activateEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = transitionEnrollment.transitionEnrollment(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "activate", requestIdempotencyKey(key))
    @PostMapping("/api/v1/enrollments/{id}/cancel")
    fun cancelEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = transitionEnrollment.transitionEnrollment(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "cancel", requestIdempotencyKey(key))
    @PostMapping("/api/v1/enrollments/{id}/end")
    fun endEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = transitionEnrollment.transitionEnrollment(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "end", requestIdempotencyKey(key))
    @GetMapping("/api/v1/me/enrollments")
    fun myEnrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String) = pagedBody(listMyEnrollments.myEnrollments(actor(cookie), page, pageSize, sort))
    @GetMapping("/api/v1/students")
    fun students(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?, @RequestParam(defaultValue = "false") history: Boolean) = pagedBody(listStudents.students(actor(cookie), page, pageSize, sort, classId, q, history))
    @GetMapping("/api/v1/students/{id}")
    fun student(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getStudent.student(actor(cookie), requestId(id))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
    private fun pagedBody(result: AcademicPage): Map<String, Any?> = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun requestInteger(value: JsonNode): Int? = if (value.isMissingNode) null else value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
}
