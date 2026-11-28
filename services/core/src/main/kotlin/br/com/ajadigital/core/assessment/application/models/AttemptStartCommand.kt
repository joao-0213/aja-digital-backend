package br.com.ajadigital.core.assessment.application.models

data class AttemptStartCommand(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val assessmentId: String,
    val key: String,
    val requestHash: String,
)
