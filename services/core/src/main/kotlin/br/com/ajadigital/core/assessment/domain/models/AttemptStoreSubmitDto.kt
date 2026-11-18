package br.com.ajadigital.core.assessment.domain.models

data class AttemptStoreSubmitDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val key: String, val requestHash: String, val expectedRevision: Int)
