package br.com.ajadigital.core.shared.domain.models

data class FindIdempotencyDto(
    val actorId: String,
    val operation: String,
    val target: String,
    val key: String,
    val requestHash: String,
)
