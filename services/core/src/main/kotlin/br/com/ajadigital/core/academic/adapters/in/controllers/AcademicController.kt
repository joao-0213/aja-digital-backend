package br.com.ajadigital.core.academic.adapters.`in`.controllers
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.WithdrawUserDto

import br.com.ajadigital.core.academic.application.models.UpdateUserDto

import br.com.ajadigital.core.academic.application.models.UpdateClassDto

import br.com.ajadigital.core.academic.application.models.TransitionEnrollmentDto

import br.com.ajadigital.core.academic.application.models.SetUserRoleDto

import br.com.ajadigital.core.academic.application.models.ReopenClassDto

import br.com.ajadigital.core.academic.application.models.PreviewEnrollmentDto

import br.com.ajadigital.core.academic.application.models.ListUsersDto

import br.com.ajadigital.core.academic.application.models.ListStudentsDto

import br.com.ajadigital.core.academic.application.models.ListMyEnrollmentsDto

import br.com.ajadigital.core.academic.application.models.ListEnrollmentsDto

import br.com.ajadigital.core.academic.application.models.ListClassesDto

import br.com.ajadigital.core.academic.application.models.DeactivateUserDto

import br.com.ajadigital.core.academic.application.models.CreateUserDto

import br.com.ajadigital.core.academic.application.models.CreateEnrollmentDto

import br.com.ajadigital.core.academic.application.models.CreateClassDto

import br.com.ajadigital.core.academic.application.models.CloseClassDto

import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.ClassCreateRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.ClassEditRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.EnrollmentCreateRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.RevisionRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.RoleRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.UserCreateRequest
import br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests.UserEditRequest
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.GetUserDto
import br.com.ajadigital.core.academic.application.models.GetClassDto
import br.com.ajadigital.core.academic.application.models.GetEnrollmentDto
import br.com.ajadigital.core.academic.application.models.GetStudentDto
import br.com.ajadigital.core.academic.application.ports.`in`.CloseClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.DeactivateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetStudentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListMyEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListUsersInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.PreviewEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ReopenClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.SetUserRoleInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.TransitionEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.WithdrawUserInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
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
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentPreview
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicStudentView
import br.com.ajadigital.core.academic.application.models.AcademicStudentSummaryView
import br.com.ajadigital.core.shared.application.contracts.Page
import tools.jackson.databind.JsonNode

@RestController
class AcademicController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listUsers: ListUsersInputPort,
    private val getUser: GetUserInputPort,
    private val createUser: CreateUserInputPort,
    private val updateUser: UpdateUserInputPort,
    private val setUserRole: SetUserRoleInputPort,
    private val deactivateUser: DeactivateUserInputPort,
    private val withdrawUser: WithdrawUserInputPort,
    private val listClasses: ListClassesInputPort,
    private val getClass: GetClassInputPort,
    private val createClass: CreateClassInputPort,
    private val updateClass: UpdateClassInputPort,
    private val closeClass: CloseClassInputPort,
    private val reopenClass: ReopenClassInputPort,
    private val listEnrollments: ListEnrollmentsInputPort,
    private val getEnrollment: GetEnrollmentInputPort,
    private val previewEnrollment: PreviewEnrollmentInputPort,
    private val createEnrollment: CreateEnrollmentInputPort,
    private val transitionEnrollment: TransitionEnrollmentInputPort,
    private val listMyEnrollments: ListMyEnrollmentsInputPort,
    private val listStudents: ListStudentsInputPort,
    private val getStudent: GetStudentInputPort,
) {
    @GetMapping("/api/v1/users")
    fun users(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) role: String?, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = pagedBody(listUsers.users(ListUsersDto(actor(cookie), page, pageSize, sort, role, active, q)))
    @PostMapping("/api/v1/users")
    fun createUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: UserCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createUser.createUser(CreateUserDto(actor(cookie), body.name, body.email, body.role, requestIdempotencyKey(key))))
    @GetMapping("/api/v1/users/{id}")
    fun user(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getUser.user(GetUserDto(actor(cookie), requestId(id)))
    @PatchMapping("/api/v1/users/{id}")
    fun updateUser(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: UserEditRequest) = updateUser.updateUser(UpdateUserDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.name), requestText(body.email)))
    @PostMapping("/api/v1/users/{id}/role")
    fun role(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RoleRequest) = setUserRole.setRole(SetUserRoleDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), body.role))
    @PostMapping("/api/v1/users/{id}/deactivate")
    fun deactivate(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = deactivateUser.deactivate(DeactivateUserDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision)))
    @PostMapping("/api/v1/users/{id}/withdraw")
    fun withdraw(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: RevisionRequest) = withdrawUser.withdraw(WithdrawUserDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision)))

    @GetMapping("/api/v1/classes")
    fun classes(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) active: Boolean?, @RequestParam(required = false) q: String?) = pagedBody(listClasses.classes(ListClassesDto(actor(cookie), page, pageSize, sort, active, q)), ::classBody)
    @PostMapping("/api/v1/classes")
    fun createClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ClassCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(classBody(createClass.createClass(CreateClassDto(actor(cookie), body.name, body.code, body.year, body.shift, requestIdempotencyKey(key)))))
    @GetMapping("/api/v1/classes/{id}")
    fun classById(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = classBody(getClass.classById(GetClassDto(actor(cookie), requestId(id))))
    @PatchMapping("/api/v1/classes/{id}")
    fun updateClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ClassEditRequest) = classBody(updateClass.updateClass(UpdateClassDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.name), requestText(body.code), requestInteger(body.year), requestText(body.shift))))
    @PostMapping("/api/v1/classes/{id}/close")
    fun closeClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = classBody(closeClass.closeClass(CloseClassDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestIdempotencyKey(key))))
    @PostMapping("/api/v1/classes/{id}/reopen")
    fun reopenClass(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = classBody(reopenClass.reopenClass(ReopenClassDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestIdempotencyKey(key))))

    @GetMapping("/api/v1/enrollments")
    fun enrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) studentId: String?, @RequestParam(required = false) status: List<String>?, @RequestParam(required = false) q: String?) = pagedBody(listEnrollments.enrollments(ListEnrollmentsDto(actor(cookie), page, pageSize, sort, classId, studentId, status, q)), ::enrollmentBody)
    @PostMapping("/api/v1/enrollments")
    fun createEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: EnrollmentCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(enrollmentBody(createEnrollment.createEnrollment(CreateEnrollmentDto(actor(cookie), requestId(body.classId), body.email, requestIdempotencyKey(key)))))
    @GetMapping("/api/v1/enrollments/preview")
    fun preview(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam classId: String, @RequestParam email: String) = previewBody(previewEnrollment.preview(PreviewEnrollmentDto(actor(cookie), requestId(classId), email)))
    @GetMapping("/api/v1/enrollments/{id}")
    fun enrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = enrollmentBody(getEnrollment.enrollment(GetEnrollmentDto(actor(cookie), requestId(id))))
    @PostMapping("/api/v1/enrollments/{id}/activate")
    fun activateEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = enrollmentBody(transitionEnrollment.transitionEnrollment(TransitionEnrollmentDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "activate", requestIdempotencyKey(key))))
    @PostMapping("/api/v1/enrollments/{id}/cancel")
    fun cancelEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = enrollmentBody(transitionEnrollment.transitionEnrollment(TransitionEnrollmentDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "cancel", requestIdempotencyKey(key))))
    @PostMapping("/api/v1/enrollments/{id}/end")
    fun endEnrollment(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: RevisionRequest) = enrollmentBody(transitionEnrollment.transitionEnrollment(TransitionEnrollmentDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), "end", requestIdempotencyKey(key))))
    @GetMapping("/api/v1/me/enrollments")
    fun myEnrollments(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String) = pagedBody(listMyEnrollments.myEnrollments(ListMyEnrollmentsDto(actor(cookie), page, pageSize, sort)), ::enrollmentBody)
    @GetMapping("/api/v1/students")
    fun students(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?, @RequestParam(defaultValue = "false") history: Boolean) = pagedBody(listStudents.students(ListStudentsDto(actor(cookie), page, pageSize, sort, classId, q, history)), ::studentSummaryBody)
    @GetMapping("/api/v1/students/{id}")
    fun student(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = studentBody(getStudent.student(GetStudentDto(actor(cookie), requestId(id))))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun <T> pagedBody(result: Page<T>, render: (T) -> Any? = { it }): Map<String, Any?> = mapOf("items" to result.items.map(render), "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun classBody(value: AcademicClassView): Map<String, Any?> = buildMap {
        put("id", value.id)
        put("revision", value.revision)
        put("createdAt", value.createdAt)
        put("updatedAt", value.updatedAt)
        put("updatedBy", value.updatedBy)
        put("name", value.name)
        put("code", value.code)
        put("year", value.year)
        put("shift", value.shift)
        put("active", value.active)
        value.studentIds?.let { put("studentIds", it) }
    }
    private fun enrollmentBody(value: AcademicEnrollmentView): Map<String, Any?> = linkedMapOf(
        "id" to value.id, "revision" to value.revision, "createdAt" to value.createdAt, "updatedAt" to value.updatedAt,
        "updatedBy" to value.updatedBy, "classId" to value.classId, "email" to value.email, "studentId" to value.studentId,
        "status" to value.status, "createdBy" to value.createdBy, "enrolledAt" to value.enrolledAt, "endedAt" to value.endedAt,
        "legacy" to value.legacy, "unassignedAssessmentIds" to value.unassignedAssessmentIds, "className" to value.className,
    )
    private fun studentBody(value: AcademicStudentView): Map<String, Any?> = linkedMapOf(
        "student" to studentSummaryBody(value.student), "enrollments" to value.enrollments.map(::enrollmentBody),
        // Legacy-only domains; the existing Academic endpoint has always returned empty arrays here.
        "attendance" to emptyList<String>(), "attempts" to emptyList<String>(), "submissions" to emptyList<String>(),
    )
    private fun studentSummaryBody(value: AcademicStudentSummaryView): Map<String, Any?> = linkedMapOf(
        "id" to value.id, "name" to value.name, "email" to value.email, "active" to value.active,
        "enrollment" to value.enrollment?.let(::enrollmentBody),
    )
    private fun previewBody(value: AcademicEnrollmentPreview): Map<String, Any?> = linkedMapOf(
        "email" to value.email,
        "classId" to value.classId,
        "account" to value.account,
        "existingEnrollment" to value.existingEnrollment?.let(::enrollmentBody),
        "otherClass" to value.otherClass,
        "canEnroll" to value.canEnroll,
        "message" to value.message,
    )
    private fun requestInteger(value: JsonNode): Int? = if (value.isMissingNode) null else value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
}
