package br.com.ajadigital.core.outbox.application.usecases

import br.com.ajadigital.core.outbox.application.ports.`in`.PublishPendingEventsInputPort
import br.com.ajadigital.core.outbox.application.ports.out.EventPublisherOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxRepositoryOutputPort
import br.com.ajadigital.core.outbox.domain.models.ClaimPendingOutboxEventsDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventFailedDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventPublishedDto
import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.PublishAvailableDto
import br.com.ajadigital.core.outbox.domain.models.PublishEventDto
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import java.time.Clock
import java.time.Instant

class PublishPendingEventsUseCase(
    private val transactions: TransactionRunnerOutputPort,
    private val repository: OutboxRepositoryOutputPort,
    private val publisher: EventPublisherOutputPort,
    private val clock: Clock,
) : PublishPendingEventsInputPort {
    override fun publishAvailable(request: PublishAvailableDto) {
        transactions.run {
            val now = Instant.now(clock)
            val events = repository.claimPending(ClaimPendingOutboxEventsDto(now, BATCH_LIMIT))
            events.forEach { event -> publishOne(event, now) }
        }
    }

    private fun publishOne(
        event: PendingOutboxEvent,
        now: Instant,
    ) {
        val failure =
            try {
                publisher.publish(PublishEventDto(event))
                null
            } catch (error: Exception) {
                error
            }
        if (failure == null) {
            repository.markPublished(MarkOutboxEventPublishedDto(event.id, now))
        } else {
            val attempts = event.attempts + 1
            val delaySeconds = (1L shl (attempts - 1).coerceAtMost(8)).coerceAtMost(300)
            repository.markFailedAttempt(
                MarkOutboxEventFailedDto(
                    event.id,
                    attempts,
                    now.plusSeconds(delaySeconds),
                    failure.javaClass.simpleName.take(MAX_FAILURE_NAME_LENGTH),
                    if (attempts >= MAX_ATTEMPTS) now else null,
                ),
            )
        }
    }

    companion object {
        private const val BATCH_LIMIT = 25
        private const val MAX_ATTEMPTS = 5
    }
}

private const val MAX_FAILURE_NAME_LENGTH = 80
