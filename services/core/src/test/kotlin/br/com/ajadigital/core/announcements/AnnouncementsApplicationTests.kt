package br.com.ajadigital.core.announcements

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.announcements.adapters.out.serialization.JacksonAnnouncementReceiptCodec
import br.com.ajadigital.core.announcements.adapters.out.serialization.JacksonAnnouncementRequestFingerprint
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementAuthor
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementOccurrenceProjection
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementCommand
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementReceiptCodecOutputPort
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import br.com.ajadigital.core.announcements.application.usecases.CreateAnnouncementUseCase
import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.announcements.application.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.outbox.application.contracts.EncryptedOutboxPayload
import br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxEventWriter
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class AnnouncementsApplicationTests {
    private val now = Instant.parse("2026-11-12T12:00:00Z")
    private val teacher = AuthenticatedActor("teacher", "teacher", true, now, "synthetic-session")
    private val student = AuthenticatedActor("student", "student", true, now, "synthetic-session")
    private val draft = AnnouncementDraft("class-1", "subject-1", "Notice", "Body", false, AnnouncementOccurrence("2026-11-12", null, true))
    private val created = Announcement("announcement-1", 1, now.toString(), now.toString(), teacher.id, draft.classId, draft.subjectId, draft.title, draft.body, draft.notify, draft.occurrence?.let { AnnouncementOccurrenceProjection.WithTime(it.date, it.time, it.allDay) }, AnnouncementAuthor(teacher.id, "Teacher"))

    @Test
    fun `create input port authorizes before persistence and replays typed receipt`() {
        val store = TestAnnouncementStore(created)
        val codec: AnnouncementReceiptCodecOutputPort = JacksonAnnouncementReceiptCodec(JsonMapper.builder().build())
        val idempotency = TestIdempotency()
        val useCase = CreateAnnouncementUseCase(
            store,
            JacksonAnnouncementRequestFingerprint(JsonMapper.builder().build()),
            idempotency,
            codec,
            object : TransactionRunnerOutputPort {
                override fun <T> run(block: () -> T): T = block()
            },
        )

        assertThrows(AcademicForbidden::class.java) { useCase.create(CreateAnnouncementCommand(student, draft, "notice-key-0001")) }
        assertThrows(IllegalArgumentException::class.java) {
            useCase.create(CreateAnnouncementCommand(teacher, draft.copy(occurrence = AnnouncementOccurrence("2026-02-30", null, true)), "notice-key-0002"))
        }
        assertEquals(0, store.authorizations)

        assertEquals(created, useCase.create(CreateAnnouncementCommand(teacher, draft, "notice-key-0001")))
        assertEquals(created, useCase.create(CreateAnnouncementCommand(teacher, draft, "notice-key-0001")))
        assertEquals(2, store.authorizations)
        assertEquals(1, store.creates)
        assertEquals(1, idempotency.saves)
    }

    private class TestAnnouncementStore(private val result: Announcement) : AnnouncementStoreOutputPort {
        var authorizations = 0
        var creates = 0
        override fun authorizeCreate(request: AuthorizeAnnouncementCreate) { authorizations++ }
        override fun create(request: CreateAnnouncementRecord): Announcement { creates++; return result }
        override fun update(request: UpdateAnnouncementRecord): Announcement? = null
        override fun withdraw(request: WithdrawAnnouncementRecord): Announcement? = null
        override fun announcement(request: GetStoredAnnouncementQuery): Announcement? = null
        override fun announcements(request: ListAnnouncementsStoreQuery): Page<Announcement> = Page(emptyList(), 1, 1, 0)
        override fun calendar(request: ListAnnouncementCalendarQuery): Page<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent> = Page(emptyList(), 1, 1, 0)
        override fun dashboardAnnouncements(request: ListDashboardAnnouncementStoreQuery): List<Announcement> = emptyList()
        override fun dashboardUpcoming(request: ListDashboardUpcomingStoreQuery): List<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent> = emptyList()
    }

    private class TestIdempotency : EncodedIdempotencyStoreOutputPort {
        private var stored: Pair<FindIdempotencyDto, String>? = null
        var saves = 0
        override fun findEncoded(request: FindIdempotencyDto): String? = stored?.let { (prior, response) ->
            if (prior.requestHash != request.requestHash) throw IdempotencyKeyReuse()
            response
        }
        override fun saveEncoded(request: FindIdempotencyDto, response: String) { saves++; stored = request to response }
    }
}
