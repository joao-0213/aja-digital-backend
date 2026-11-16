package br.com.ajadigital.core.outbox.domain.usecases

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.ports.`in`.PublishPendingEventsUseCase
import br.com.ajadigital.core.outbox.domain.ports.out.EventPublisher
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepository
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import java.time.Clock
import java.time.Instant

class PublishPendingEvents(
    private val transactions: TransactionRunner,
    private val repository: OutboxRepository,
    private val publisher: EventPublisher,
    private val clock: Clock,
) : PublishPendingEventsUseCase {
    override fun publishAvailable() {
        transactions.run {
            val now = Instant.now(clock)
            val events = repository.claimPending(now, BATCH_LIMIT)
            events.forEach { event -> publishOne(event, now) }
        }
    }

    private fun publishOne(event: PendingOutboxEvent, now: Instant) {
        val failure = try {
            publisher.publish(event)
            null
        } catch (error: Exception) {
            error
        }
        if (failure == null) {
            repository.markPublished(event.id, now)
        } else {
            val attempts = event.attempts + 1
            val delaySeconds = (1L shl (attempts - 1).coerceAtMost(8)).coerceAtMost(300)
            repository.markFailedAttempt(
                id = event.id,
                attemptCount = attempts,
                nextAttemptAt = now.plusSeconds(delaySeconds),
                lastError = failure.javaClass.simpleName.take(80),
                failedAt = if (attempts >= MAX_ATTEMPTS) now else null,
            )
        }
    }

    companion object {
        private const val BATCH_LIMIT = 25
        private const val MAX_ATTEMPTS = 5
    }
}
