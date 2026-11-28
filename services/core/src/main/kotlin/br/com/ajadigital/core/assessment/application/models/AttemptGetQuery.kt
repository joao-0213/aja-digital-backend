package br.com.ajadigital.core.assessment.application.models

data class AttemptGetQuery(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val id: String,
)
