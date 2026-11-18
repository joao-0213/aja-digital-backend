package br.com.ajadigital.core.assessment.domain.models


data class AttemptStoreStartDto(val actorId: String, val role: String, val sessionHash: String, val assessmentId: String, val key: String, val requestHash: String)
