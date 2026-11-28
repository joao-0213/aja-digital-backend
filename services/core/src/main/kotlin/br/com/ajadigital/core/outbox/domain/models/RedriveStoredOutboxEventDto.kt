package br.com.ajadigital.core.outbox.domain.models

import java.time.Instant

data class RedriveStoredOutboxEventDto(
    val id: String,
    val now: Instant,
)
