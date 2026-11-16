package br.com.ajadigital.core.announcements.adapters.`in`.controllers
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementCreateRequest
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementEditRequest
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementRevisionRequest

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.announcements.domain.usecases.ManageAnnouncements
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.models.AnnouncementOccurrence
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
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
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.MissingNode


@RestController
class AnnouncementController(private val auth: br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent, private val announcements: ManageAnnouncements, private val mapper: ObjectMapper) {
    @GetMapping("/api/v1/announcements")
    fun list(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(required = false) classId: String?,
        @RequestParam(required = false) subjectId: String?,
        @RequestParam(required = false) q: String?,
    ) = page(announcements.announcements(actor(cookie), page, pageSize, sort, classId?.let(::id), subjectId?.let(::id), q))

    @PostMapping("/api/v1/announcements")
    @Transactional
    fun create(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestHeader(name = "Idempotency-Key", required = false) key: String?,
        @RequestBody body: AnnouncementCreateRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val draft = AnnouncementDraft(id(body.classId), nullableId(body.subjectId), body.title, body.body, boolean(body.notify), occurrence(body.occurrence))
        return ResponseEntity.status(HttpStatus.CREATED).body(announcements.create(actor(cookie), draft, key ?: throw IllegalArgumentException()))
    }

    @GetMapping("/api/v1/announcements/{id}")
    fun get(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = announcements.announcement(actor(cookie), id(id))

    @PatchMapping("/api/v1/announcements/{id}")
    @Transactional
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AnnouncementEditRequest): Map<String, Any?> {
        val principal = actor(cookie)
        val announcementId = id(id)
        val current = announcements.announcement(principal, announcementId)
        val occurrence = if (body.occurrence.isMissingNode) current["occurrence"]?.let(::mapperNode)?.let(::occurrence) else occurrence(body.occurrence)
        val draft = AnnouncementDraft(
            if (body.classId.isMissingNode) current.getValue("classId") as String else text(body.classId).let(::id),
            if (body.subjectId.isMissingNode) current["subjectId"] as String? else nullableId(body.subjectId),
            if (body.title.isMissingNode) current.getValue("title") as String else text(body.title),
            if (body.body.isMissingNode) current.getValue("body") as String else text(body.body),
            if (body.notify.isMissingNode) current.getValue("notify") as Boolean else boolean(body.notify),
            occurrence,
        )
        return announcements.update(principal, announcementId, revision(body.expectedRevision), draft)
    }

    @PostMapping("/api/v1/announcements/{id}/withdraw")
    @Transactional
    fun withdraw(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AnnouncementRevisionRequest): Map<String, Any?> =
        announcements.withdraw(actor(cookie), id(id), revision(body.expectedRevision))

    @GetMapping("/api/v1/calendar")
    fun calendar(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "date") sort: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) subjectId: String?,
    ) = page(announcements.calendar(actor(cookie), page, pageSize, sort, from, to, subjectId?.let(::id)))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
    private fun page(result: br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun id(value: String) = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
    private fun nullableId(node: JsonNode): String? = if (node.isNull) null else id(text(node))
    private fun text(node: JsonNode) = node.asString().also { require(node.isTextual) }
    private fun boolean(node: JsonNode) = node.booleanValue().also { require(node.isBoolean) }
    private fun revision(node: JsonNode) = node.intValue().also { require(node.isIntegralNumber && node.canConvertToInt() && it > 0) }
    private fun occurrence(node: JsonNode): AnnouncementOccurrence? {
        if (node.isNull) return null
        require(node.isObject && node.has("date") && node.has("time") && node.has("allDay"))
        require(node.properties().all { it.key in setOf("date", "time", "allDay") })
        val time = node.get("time")
        require(time.isNull || time.isTextual)
        val allDay = node.get("allDay")
        require(allDay.isBoolean)
        return AnnouncementOccurrence(text(node.get("date")), if (time.isNull) null else text(time), allDay.booleanValue())
    }
    private fun mapperNode(value: Any?): JsonNode = when (value) {
        null -> MissingNode.getInstance()
        is Map<*, *> -> mapper.valueToTree(value)
        else -> MissingNode.getInstance()
    }
}
