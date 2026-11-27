package br.com.ajadigital.core.outbox.application.contracts

import java.time.Instant

data class AppendPreparedOutboxEventCommand(val event: PreparedOutboxEvent, val createdAt: Instant)
