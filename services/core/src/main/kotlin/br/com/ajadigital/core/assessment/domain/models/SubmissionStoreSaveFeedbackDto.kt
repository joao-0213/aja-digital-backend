package br.com.ajadigital.core.assessment.domain.models

data class SubmissionStoreSaveFeedbackDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val expectedRevision: Int, val comment: String, val grade: java.math.BigDecimal?)
