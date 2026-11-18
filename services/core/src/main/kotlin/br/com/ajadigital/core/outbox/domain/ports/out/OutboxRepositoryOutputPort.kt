package br.com.ajadigital.core.outbox.domain.ports.out

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.ClaimPendingOutboxEventsDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventPublishedDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventFailedDto
import br.com.ajadigital.core.outbox.domain.models.RedriveStoredOutboxEventDto

interface OutboxRepositoryOutputPort {
    fun claimPending(request: ClaimPendingOutboxEventsDto): List<PendingOutboxEvent>
    fun markPublished(request: MarkOutboxEventPublishedDto)
    fun markFailedAttempt(request: MarkOutboxEventFailedDto)
    fun redrive(request: RedriveStoredOutboxEventDto): Boolean
}
