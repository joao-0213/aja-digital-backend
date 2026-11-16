package br.com.ajadigital.core.outbox.domain.ports.out

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import java.time.Instant

interface OutboxRepository {
    fun claimPending(now: Instant, limit: Int): List<PendingOutboxEvent>
    fun markPublished(id: String, now: Instant)
    fun markFailedAttempt(id: String, attemptCount: Int, nextAttemptAt: Instant, lastError: String, failedAt: Instant?)
    fun redrive(id: String, now: Instant): Boolean
}
