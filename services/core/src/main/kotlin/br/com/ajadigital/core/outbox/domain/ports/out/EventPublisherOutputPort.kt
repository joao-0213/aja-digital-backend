package br.com.ajadigital.core.outbox.domain.ports.out

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.PublishEventDto

interface EventPublisherOutputPort {
    fun publish(request: PublishEventDto)
}
