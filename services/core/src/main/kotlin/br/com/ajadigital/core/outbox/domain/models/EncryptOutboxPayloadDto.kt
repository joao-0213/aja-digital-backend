package br.com.ajadigital.core.outbox.domain.models

data class EncryptOutboxPayloadDto(val eventId: String, val eventType: String, val version: Int, val plaintext: ByteArray)
