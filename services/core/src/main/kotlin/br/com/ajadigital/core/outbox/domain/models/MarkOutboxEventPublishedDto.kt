package br.com.ajadigital.core.outbox.domain.models

import java.time.Instant

data class MarkOutboxEventPublishedDto(
    val id: String,
    val now: Instant,
)
