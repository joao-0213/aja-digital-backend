package br.com.ajadigital.core.outbox.domain.ports.out

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent

interface EventPublisher {
    fun publish(event: PendingOutboxEvent)
}
