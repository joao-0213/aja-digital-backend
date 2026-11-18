package br.com.ajadigital.core.assessment.domain.models

data class SubmissionStoreUpdateDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val expectedRevision: Int, val attachments: List<SubmissionAttachment>?)
