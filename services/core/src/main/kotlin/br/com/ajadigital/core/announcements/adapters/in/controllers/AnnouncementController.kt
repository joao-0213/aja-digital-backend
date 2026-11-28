package br.com.ajadigital.core.announcements.adapters.`in`.controllers
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementCreateRequest
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementEditRequest
import br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests.AnnouncementRevisionRequest
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementOccurrenceProjection
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.GetAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsQuery
import br.com.ajadigital.core.announcements.application.models.ListCalendarEventsQuery
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementCommand
import br.com.ajadigital.core.announcements.application.ports.`in`.CreateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.GetAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListCalendarEventsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.UpdateAnnouncementInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.WithdrawAnnouncementInputPort
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.models.AnnouncementOccurrence
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.shared.application.contracts.Page
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
class AnnouncementController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val createAnnouncement: CreateAnnouncementInputPort,
    private val updateAnnouncement: UpdateAnnouncementInputPort,
    private val withdrawAnnouncement: WithdrawAnnouncementInputPort,
    private val getAnnouncement: GetAnnouncementInputPort,
    private val listAnnouncements: ListAnnouncementsInputPort,
    private val listCalendarEvents: ListCalendarEventsInputPort,
) {
    @GetMapping("/api/v1/announcements")
    fun list(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "createdAt") sort: String,
        @RequestParam(required = false) classId: String?,
        @RequestParam(required = false) subjectId: String?,
        @RequestParam(required = false) q: String?,
    ) = announcementPage(listAnnouncements.announcements(ListAnnouncementsQuery(actor(cookie), page, pageSize, sort, classId?.let(::id), subjectId?.let(::id), q)))

    @PostMapping("/api/v1/announcements")
    fun create(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestHeader(name = "Idempotency-Key", required = false) key: String?,
        @RequestBody body: AnnouncementCreateRequest,
    ): ResponseEntity<Map<String, Any?>> {
        val draft = AnnouncementDraft(id(body.classId), nullableId(body.subjectId), body.title, body.body, boolean(body.notify), occurrence(body.occurrence))
        return ResponseEntity.status(HttpStatus.CREATED).body(createAnnouncement.create(CreateAnnouncementCommand(actor(cookie), draft, key ?: throw IllegalArgumentException())).response())
    }

    @GetMapping("/api/v1/announcements/{id}")
    fun get(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getAnnouncement.announcement(GetAnnouncementQuery(actor(cookie), id(id))).response()

    @PatchMapping("/api/v1/announcements/{id}")
    fun update(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AnnouncementEditRequest): Map<String, Any?> {
        val principal = actor(cookie)
        val announcementId = id(id)
        val current = getAnnouncement.announcement(GetAnnouncementQuery(principal, announcementId))
        val occurrence = if (body.occurrence.isMissingNode) {
            current.occurrence?.let {
                require(it is AnnouncementOccurrenceProjection.WithTime)
                AnnouncementOccurrence(it.date, it.time, it.allDay)
            }
        } else {
            occurrence(body.occurrence)
        }
        val draft = AnnouncementDraft(
            if (body.classId.isMissingNode) current.classId else text(body.classId).let(::id),
            if (body.subjectId.isMissingNode) current.subjectId else nullableId(body.subjectId),
            if (body.title.isMissingNode) current.title else text(body.title),
            if (body.body.isMissingNode) current.body else text(body.body),
            if (body.notify.isMissingNode) current.notify else boolean(body.notify),
            occurrence,
        )
        return updateAnnouncement.update(UpdateAnnouncementCommand(principal, announcementId, revision(body.expectedRevision), draft)).response()
    }

    @PostMapping("/api/v1/announcements/{id}/withdraw")
    fun withdraw(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: AnnouncementRevisionRequest): Map<String, Any?> = withdrawAnnouncement.withdraw(WithdrawAnnouncementCommand(actor(cookie), id(id), revision(body.expectedRevision))).response()

    @GetMapping("/api/v1/calendar")
    fun calendar(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "date") sort: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) subjectId: String?,
    ) = calendarPage(listCalendarEvents.calendar(ListCalendarEventsQuery(actor(cookie), page, pageSize, sort, from, to, subjectId?.let(::id))))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun announcementPage(result: Page<Announcement>) = mapOf("items" to result.items.map { it.response() }, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun calendarPage(result: Page<AnnouncementCalendarEvent>) = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
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
    private fun br.com.ajadigital.core.announcements.application.contracts.Announcement.response() = linkedMapOf(
        "id" to id, "revision" to revision, "createdAt" to createdAt, "updatedAt" to updatedAt, "updatedBy" to updatedBy,
        "classId" to classId, "subjectId" to subjectId, "title" to title, "body" to body, "notify" to notify,
        "occurrence" to occurrence,
        "author" to linkedMapOf("id" to author.id, "name" to author.name),
    )
}
