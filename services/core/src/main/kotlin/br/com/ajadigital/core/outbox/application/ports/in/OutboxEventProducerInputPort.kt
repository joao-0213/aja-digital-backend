package br.com.ajadigital.core.outbox.application.ports.`in`

import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PrepareOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.PreparedOutboxEvent

interface OutboxEventProducerInputPort {
    fun prepare(request: PrepareOutboxEventCommand): PreparedOutboxEvent
    fun append(request: AppendPreparedOutboxEventCommand)
}
