package br.com.ajadigital.core.outbox.domain.usecases

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.ports.out.EventPublisher
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepository
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PublishPendingEventsTests {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val transactions = object : TransactionRunner {
        override fun <T> run(block: () -> T): T = block()
    }

    @Test
    fun `publishes pending events and marks them published`() {
        val event = PendingOutboxEvent("id-1", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 0)
        val publishedIds = mutableListOf<String>()
        val repo = object : OutboxRepository {
            override fun claimPending(now: Instant, limit: Int) = listOf(event)
            override fun markPublished(id: String, now: Instant) { publishedIds += id }
            override fun markFailedAttempt(id: String, attemptCount: Int, nextAttemptAt: Instant, lastError: String, failedAt: Instant?) = Unit
            override fun redrive(id: String, now: Instant) = false
        }
        val publisher = object : EventPublisher {
            override fun publish(event: PendingOutboxEvent) = Unit
        }

        PublishPendingEvents(transactions, repo, publisher, clock).publishAvailable()

        assertEquals(listOf("id-1"), publishedIds)
    }

    @Test
    fun `marks failed attempt with exponential backoff and sets failedAt on max attempts`() {
        val event1 = PendingOutboxEvent("id-1", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 0)
        val event2 = PendingOutboxEvent("id-2", "identity.student.registered", 1, "key-1", ByteArray(12), ByteArray(16), 4)
        val failures = mutableListOf<Triple<String, Int, Instant?>>()
        val nextAttempts = mutableMapOf<String, Instant>()
        val repo = object : OutboxRepository {
            override fun claimPending(now: Instant, limit: Int) = listOf(event1, event2)
            override fun markPublished(id: String, now: Instant) = Unit
            override fun markFailedAttempt(id: String, attemptCount: Int, nextAttemptAt: Instant, lastError: String, failedAt: Instant?) {
                failures += Triple(id, attemptCount, failedAt)
                nextAttempts[id] = nextAttemptAt
            }
            override fun redrive(id: String, now: Instant) = false
        }
        val publisher = object : EventPublisher {
            override fun publish(event: PendingOutboxEvent): Unit = throw IllegalStateException("boom")
        }

        PublishPendingEvents(transactions, repo, publisher, clock).publishAvailable()

        assertEquals(2, failures.size)
        assertEquals(1, failures[0].second)
        assertNull(failures[0].third)
        assertEquals(now.plusSeconds(1), nextAttempts["id-1"])

        assertEquals(5, failures[1].second)
        assertEquals(now, failures[1].third)
        assertEquals(now.plusSeconds(16), nextAttempts["id-2"])
    }
}
