package br.com.ajadigital.core.assessment.domain.models


data class AssessmentStoreCreateDto(val actorId: String, val role: String, val sessionHash: String, val key: String, val requestHash: String, val draft: AssessmentDraft, val questions: List<AssessmentQuestion>)
