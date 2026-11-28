package br.com.ajadigital.core.outbox.domain.models

class EncryptIdentityNotificationDto(
    val eventId: String,
    val eventType: String,
    val version: Int,
    val recipient: String,
    val token: String,
    val userId: String,
    val purpose: String,
)
