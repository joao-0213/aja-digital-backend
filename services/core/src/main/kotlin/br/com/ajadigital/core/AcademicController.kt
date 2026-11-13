package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageAcademics
import br.com.ajadigital.core.application.academic.ManageAttendance
import br.com.ajadigital.core.application.academic.ManageSubjects
import br.com.ajadigital.core.application.academic.ManageTopics
import br.com.ajadigital.core.ports.out.AttendanceDraft
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException


@RestController
class AcademicController(private val auth: AuthenticateStudent, private val academics: ManageAcademics, private val subjects: ManageSubjects, private val topics: ManageTopics, private val attendance: ManageAttendance) {
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

    @GetMapping("/api/v1/subjects")
    fun subjects(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?) = page(subjects.subjects(actor(cookie), page, pageSize, sort, classId?.let(::id), q))
    @PostMapping("/api/v1/subjects")
    @Transactional
    fun createSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: SubjectCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(subjects.createSubject(actor(cookie), id(body.classId), body.name, body.teacherIds.map(::id), body.color, body.icon, body.description, idempotencyKey(key)))
    @GetMapping("/api/v1/subjects/{id}")
    fun subject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String): Map<String, Any?> {
        val principal = actor(cookie); val subjectId = id(id); val subject = subjects.subject(principal, subjectId)
        val publicationIds = subject["publicationIds"] as List<*>
        return mapOf("subject" to subject, "modules" to subjects.subjectModules(principal, subjectId), "projections" to mapOf("materials" to mapOf("count" to publicationIds.count { (it as String).startsWith("material:") }), "assessments" to mapOf("count" to publicationIds.count { (it as String).startsWith("assessment:") }), "announcements" to mapOf("count" to publicationIds.count { (it as String).startsWith("notice:") })))
    }
    @PatchMapping("/api/v1/subjects/{id}")
    @Transactional
    fun updateSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: SubjectEditRequest) = subjects.updateSubject(actor(cookie), id(id), revision(body.expectedRevision), text(body.classId), text(body.name), strings(body.teacherIds), text(body.color), text(body.icon), text(body.description))
    @GetMapping("/api/v1/subjects/{id}/visits")
    fun getVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = subjects.visit(actor(cookie), id(id))
    @PostMapping("/api/v1/subjects/{id}/visits")
    @Transactional
    fun recordVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: VisitCommandRequest) = subjects.visit(actor(cookie), id(id), body.publicationIds)

    @GetMapping("/api/v1/modules")
    fun modules(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) subjectId: String?) = page(subjects.modules(actor(cookie), page, pageSize, sort, subjectId?.let(::id)))
    @PostMapping("/api/v1/modules")
    @Transactional
    fun createModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ModuleCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(subjects.createModule(actor(cookie), id(body.subjectId), body.title, body.description, idempotencyKey(key)))
    @GetMapping("/api/v1/modules/{id}")
    fun module(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = subjects.module(actor(cookie), id(id))
    @PatchMapping("/api/v1/modules/{id}")
    @Transactional
    fun updateModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ModuleEditRequest) = subjects.updateModule(actor(cookie), id(id), revision(body.expectedRevision), text(body.title), text(body.description))
    @PostMapping("/api/v1/subjects/{id}/reorder-modules")
    @Transactional
    fun reorderModules(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = subjects.reorder(actor(cookie), id(id), revision(body.expectedRevision), body.items.map { id(it.id) to it.expectedRevision }, page, pageSize)

    @GetMapping("/api/v1/topics")
    fun topics(@CookieValue(name = "aja_session", required = false) cookie: String?, request: HttpServletRequest, @RequestParam(defaultValue = "1") page: String, @RequestParam(defaultValue = "20") pageSize: String, @RequestParam(defaultValue = "createdAt") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) q: String?, @RequestParam(required = false) unread: String?, @RequestParam(required = false) solved: String?): Map<String, Any?> {
        val currentActor = actor(cookie)
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "classId", "subjectId", "q", "unread", "solved") })
        fun boolean(value: String?) = when (value) { null -> null; "true" -> true; "false" -> false; else -> throw IllegalArgumentException() }
        return topics.topics(currentActor, page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, classId, subjectId, q, boolean(unread), boolean(solved))
    }
    @PostMapping("/api/v1/topics")
    fun createTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(topics.create(actor(cookie), br.com.ajadigital.core.ports.out.TopicDraft(body.classId, body.subjectId, body.title, body.body, body.tags, body.target?.type, body.target?.id, body.attachmentId), idempotencyKey(key)))
    @GetMapping("/api/v1/topics/{id}")
    fun topic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = topics.topic(actor(cookie), id(id))
    @PostMapping("/api/v1/topics/{id}/replies")
    @Transactional
    fun reply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicReplyRequest) = ResponseEntity.status(HttpStatus.CREATED).body(topics.reply(actor(cookie), id(id), body.body, idempotencyKey(key)))
    @PostMapping("/api/v1/topics/{id}/read")
    @Transactional
    fun readTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: EmptyCommandRequest) = topics.read(actor(cookie), id(id))
    @PostMapping("/api/v1/topics/{id}/solution")
    @Transactional
    fun solution(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicSolutionRequest): Map<String, Any?> {
        val replyId = when { body.replyId.isNull -> null; body.replyId.isTextual -> id(body.replyId.asString()); else -> throw IllegalArgumentException() }
        return topics.solution(actor(cookie), id(id), revision(body.expectedRevision), replyId)
    }
    @PostMapping("/api/v1/topics/{id}/withdraw")
    @Transactional
    fun withdrawTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicRevisionRequest) = topics.withdraw(actor(cookie), id(id), body.expectedRevision)
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/vote")
    @Transactional
    fun voteReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicVoteRequest) = topics.vote(actor(cookie), id(id), replyId, body.voted)
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/withdraw")
    @Transactional
    fun withdrawReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicRevisionRequest) = topics.withdrawReply(actor(cookie), id(id), replyId, body.expectedRevision)

    @GetMapping("/api/v1/attendance")
    fun attendanceList(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "date") sort: String, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) studentId: String?, @RequestParam(required = false) from: String?, @RequestParam(required = false) to: String?) = page(attendance.list(actor(cookie), page, pageSize, sort, subjectId, studentId, from, to))
    @GetMapping("/api/v1/attendance/eligible")
    fun eligibleAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam subjectId: String, @RequestParam date: String, @RequestParam time: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int) = page(attendance.eligible(actor(cookie), subjectId, date, time, page, pageSize))
    @GetMapping("/api/v1/attendance/{id}")
    fun attendanceById(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = attendance.attendance(actor(cookie), id(id))
    @PostMapping("/api/v1/attendance")
    @Transactional
    fun createAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: AttendanceCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(attendance.create(actor(cookie), AttendanceDraft(id(body.subjectId), attendanceDate(body.date), attendanceTime(body.time), records(body.records)), idempotencyKey(key)))
    @PatchMapping("/api/v1/attendance/{id}")
    @Transactional
    fun updateAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AttendanceEditRequest) = attendance.update(actor(cookie), id(id), revision(body.expectedRevision), records(body.records))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
    private fun idempotencyKey(value: String?): String = value?.also { require(it.length in 8..128) } ?: throw IllegalArgumentException()
    private fun id(value: String) = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
    private fun text(value: JsonNode) = if (value.isMissingNode) null else value.asText().also { require(value.isTextual) }
    private fun strings(value: JsonNode): List<String>? = if (value.isMissingNode) null else { require(value.isArray && (0 until value.size()).all { value.get(it).isTextual }); (0 until value.size()).map { id(value.get(it).asString()) } }
    private fun records(value: JsonNode): Map<String, Boolean> = run { require(value.isObject); value.properties().associate { (key, item) -> id(key) to item.booleanValue().also { require(item.isBoolean) } } }
    private fun attendanceTime(value: String) = value.also { require(it.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))) }.let(LocalTime::parse)
    private fun attendanceDate(value: String) = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException() }
    private fun integer(value: JsonNode) = if (value.isMissingNode) null else value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt()) }
    private fun revision(value: JsonNode) = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt() && it > 0) }
    private fun page(result: br.com.ajadigital.core.ports.out.AcademicPage) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
}
