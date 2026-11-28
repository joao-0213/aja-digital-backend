package br.com.ajadigital.core.outbox.domain.models

import java.time.Instant

data class MarkOutboxEventFailedDto(
    val id: String,
    val attemptCount: Int,
    val nextAttemptAt: Instant,
    val lastError: String,
    val failedAt: Instant?,
)
