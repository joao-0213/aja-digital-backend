package br.com.ajadigital.core.assessment.domain.models

data class SubmissionStorePublishFeedbackDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val expectedRevision: Int, val key: String, val requestHash: String)
