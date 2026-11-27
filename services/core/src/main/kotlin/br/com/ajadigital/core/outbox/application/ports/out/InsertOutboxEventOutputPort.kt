package br.com.ajadigital.core.outbox.application.ports.out

import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand

interface InsertOutboxEventOutputPort {
    fun insert(request: AppendPreparedOutboxEventCommand)
}
