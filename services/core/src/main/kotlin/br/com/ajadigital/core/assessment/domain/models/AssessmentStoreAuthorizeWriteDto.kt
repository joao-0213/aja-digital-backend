package br.com.ajadigital.core.assessment.domain.models

data class AssessmentStoreAuthorizeWriteDto(val actorId: String, val role: String, val sessionHash: String, val moduleId: String)
