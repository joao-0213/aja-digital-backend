package br.com.ajadigital.core.attendance.adapters.`in`.controllers
import br.com.ajadigital.core.attendance.application.models.*

import br.com.ajadigital.core.attendance.adapters.`in`.controllers.models.requests.AttendanceCreateRequest
import br.com.ajadigital.core.attendance.adapters.`in`.controllers.models.requests.AttendanceEditRequest
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsQuery
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsQuery
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionQuery
import br.com.ajadigital.core.attendance.application.models.RecordAttendanceCommand
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceCommand
import br.com.ajadigital.core.attendance.application.ports.`in`.GetAttendanceSessionInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.ListAttendanceSessionsInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.ListEligibleAttendanceStudentsInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.RecordAttendanceInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.UpdateAttendanceInputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestId
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestIdempotencyKey
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestRevision
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException


@RestController
class AttendanceController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listAttendanceSessions: ListAttendanceSessionsInputPort,
    private val listEligibleAttendanceStudents: ListEligibleAttendanceStudentsInputPort,
    private val getAttendanceSession: GetAttendanceSessionInputPort,
    private val recordAttendance: RecordAttendanceInputPort,
    private val updateAttendance: UpdateAttendanceInputPort,
) {
    @GetMapping("/api/v1/attendance")
    fun attendanceList(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "date") sort: String, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) studentId: String?, @RequestParam(required = false) from: String?, @RequestParam(required = false) to: String?) = pagedBody(listAttendanceSessions.list(ListAttendanceSessionsQuery(actor(cookie), page, pageSize, sort, subjectId, studentId, from, to)))
    @GetMapping("/api/v1/attendance/eligible")
    fun eligibleAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam subjectId: String, @RequestParam date: String, @RequestParam time: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int) = pagedBody(listEligibleAttendanceStudents.eligible(ListEligibleAttendanceStudentsQuery(actor(cookie), subjectId, date, time, page, pageSize)))
    @GetMapping("/api/v1/attendance/{id}")
    fun attendanceById(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getAttendanceSession.attendance(GetAttendanceSessionQuery(actor(cookie), requestId(id)))
    @PostMapping("/api/v1/attendance")
    fun createAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: AttendanceCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(recordAttendance.create(RecordAttendanceCommand(actor(cookie), AttendanceDraft(requestId(body.subjectId), attendanceDate(body.date), attendanceTime(body.time), records(body.records)), requestIdempotencyKey(key))))
    @PatchMapping("/api/v1/attendance/{id}")
    fun updateAttendance(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AttendanceEditRequest) = updateAttendance.update(UpdateAttendanceCommand(actor(cookie), requestId(id), requestRevision(body.expectedRevision), records(body.records)))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun pagedBody(result: AttendanceSessionPage): Map<String, Any?> = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun pagedBody(result: AttendanceEligibleStudentPage): Map<String, Any?> = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun records(value: JsonNode): Map<String, Boolean> = run { require(value.isObject); value.properties().associate { (key, item) -> requestId(key) to item.booleanValue().also { require(item.isBoolean) } } }
    private fun attendanceTime(value: String) = value.also { require(it.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$"))) }.let(LocalTime::parse)
    private fun attendanceDate(value: String) = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException() }
}
