package br.com.ajadigital.core.outbox.domain.models

import java.time.Instant

data class ClaimPendingOutboxEventsDto(
    val now: Instant,
    val limit: Int,
)
