package br.com.ajadigital.core.announcements.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.ports.`in`.ManageAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.out.AnnouncementStore
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

class ManageAnnouncements(
    private val store: AnnouncementStore,
    private val mapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : ManageAnnouncementsUseCase {
    override fun create(actor: AcademicActor, draft: AnnouncementDraft, key: String): Map<String, Any?> = transactionRunner.run {
        requireWriter(actor)
        validate(draft)
        require(key.length in 8..128)
        store.authorizeCreate(actor.id, actor.role, actor.sessionTokenHash, draft)
        val hash = sha256(mapper.writeValueAsBytes(draft))
        try { idempotency.find(actor.id, "createAnnouncement", "/announcements", key, hash)?.let { return@run it } }
        catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
        store.create(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash).also {
            idempotency.save(actor.id, "createAnnouncement", "/announcements", key, hash, it)
        }
    }

    override fun update(actor: AcademicActor, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?> = transactionRunner.run {
        requireWriter(actor)
        require(revision > 0)
        validate(draft)
        store.update(actor.id, actor.role, actor.sessionTokenHash, id, revision, draft)
            ?: store.announcement(actor.id, actor.role, actor.sessionTokenHash, id)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }

    override fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> = transactionRunner.run {
        requireWriter(actor)
        require(revision > 0)
        store.withdraw(actor.id, actor.role, actor.sessionTokenHash, id, revision)
            ?: store.announcement(actor.id, actor.role, actor.sessionTokenHash, id)?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    }

    override fun announcement(actor: AcademicActor, id: String) = requireReader(actor).let {
        store.announcement(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }

    override fun announcements(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage {
        requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("createdAt", "-createdAt"))
        return store.announcements(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, classId, subjectId, query?.trim())
    }

    override fun calendar(actor: AcademicActor, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage {
        requireReader(actor)
        require(page > 0 && size in 1..100 && sort in setOf("date", "-date"))
        val start = from?.let(::parseDate)
        val end = to?.let(::parseDate)
        require(start == null || end == null || !start.isAfter(end))
        return store.calendar(actor.id, actor.role, actor.sessionTokenHash, page, size, sort, start?.toString(), end?.toString(), subjectId)
    }

    override fun dashboardAnnouncements(actor: AcademicActor, size: Int) = requireReader(actor).let {
        store.dashboardAnnouncements(actor.id, actor.role, actor.sessionTokenHash, size)
    }

    override fun dashboardUpcoming(actor: AcademicActor, from: LocalDate, to: LocalDate, size: Int) = requireReader(actor).let {
        store.dashboardUpcoming(actor.id, actor.role, actor.sessionTokenHash, from.toString(), to.toString(), size)
    }

    private fun validate(draft: AnnouncementDraft) {
        require(draft.classId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")))
        require(draft.subjectId == null || draft.subjectId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.length <= 10000)
        draft.occurrence?.let {
            parseDate(it.date)
            require(it.time == null || it.time.matches(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")))
            it.time?.let(LocalTime::parse)
        }
    }

    private fun requireReader(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    private fun requireWriter(actor: AcademicActor) {
        if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden()
    }

    private fun parseDate(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException() }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
