package br.com.ajadigital.core.assessment.domain.models

data class AttemptStoreSaveAnswersDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val expectedRevision: Int, val answers: Map<String, Int?>)
