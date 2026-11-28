package br.com.ajadigital.core.outbox.domain.usecases

import br.com.ajadigital.core.outbox.application.usecases.PublishPendingEventsUseCase
import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.PublishAvailableDto
import br.com.ajadigital.core.outbox.domain.models.PublishEventDto
import br.com.ajadigital.core.outbox.domain.models.ClaimPendingOutboxEventsDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventPublishedDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventFailedDto
import br.com.ajadigital.core.outbox.domain.models.RedriveStoredOutboxEventDto
import br.com.ajadigital.core.outbox.application.ports.out.EventPublisherOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxRepositoryOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublishPendingEventsTests {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val transactions = object : TransactionRunnerOutputPort {
        override fun <T> run(block: () -> T): T = block()
    }

    @Test
    fun `publishes pending events and marks them published`() {
        val event = PendingOutboxEvent("id-1", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 0)
        val publishedIds = mutableListOf<String>()
        val repo = object : OutboxRepositoryOutputPort {
            override fun claimPending(request: ClaimPendingOutboxEventsDto) = listOf(event)
            override fun markPublished(request: MarkOutboxEventPublishedDto) { publishedIds += request.id }
            override fun markFailedAttempt(request: MarkOutboxEventFailedDto) = Unit
            override fun redrive(request: RedriveStoredOutboxEventDto) = false
        }
        val publisher = object : EventPublisherOutputPort {
            override fun publish(request: PublishEventDto) = Unit
        }

        PublishPendingEventsUseCase(transactions, repo, publisher, clock).publishAvailable(PublishAvailableDto)

        assertEquals(listOf("id-1"), publishedIds)
    }

    @Test
    fun `marks failed attempt with exponential backoff and sets failedAt on max attempts`() {
        val event1 = PendingOutboxEvent("id-1", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 0)
        val event2 = PendingOutboxEvent("id-2", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 4)
        val failures = mutableListOf<Triple<String, Int, Instant?>>()
        val nextAttempts = mutableMapOf<String, Instant>()
        val lastErrors = mutableMapOf<String, String>()
        val repo = object : OutboxRepositoryOutputPort {
            override fun claimPending(request: ClaimPendingOutboxEventsDto) = listOf(event1, event2)
            override fun markPublished(request: MarkOutboxEventPublishedDto) = Unit
            override fun markFailedAttempt(request: MarkOutboxEventFailedDto) {
                failures += Triple(request.id, request.attemptCount, request.failedAt)
                nextAttempts[request.id] = request.nextAttemptAt
                lastErrors[request.id] = request.lastError
            }
            override fun redrive(request: RedriveStoredOutboxEventDto) = false
        }
        val publisher = object : EventPublisherOutputPort {
            override fun publish(request: PublishEventDto): Unit = throw IllegalStateException("boom")
        }

        PublishPendingEventsUseCase(transactions, repo, publisher, clock).publishAvailable(PublishAvailableDto)

        assertEquals(2, failures.size)
        assertEquals(1, failures[0].second)
        assertNull(failures[0].third)
        assertEquals(now.plusSeconds(1), nextAttempts["id-1"])
        assertEquals("IllegalStateException", lastErrors["id-1"])

        assertEquals(5, failures[1].second)
        assertEquals(now, failures[1].third)
        assertEquals(now.plusSeconds(16), nextAttempts["id-2"])
        assertEquals("IllegalStateException", lastErrors["id-2"])
    }
}
