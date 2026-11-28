package br.com.ajadigital.core.identity.application.models

data class RecordIdentityAuditDto(
    val eventType: String,
    val outcome: String,
    val subjectUserId: String? = null,
)
