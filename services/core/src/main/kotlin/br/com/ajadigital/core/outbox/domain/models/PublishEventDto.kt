package br.com.ajadigital.core.outbox.domain.models

data class PublishEventDto(
    val event: PendingOutboxEvent,
)
