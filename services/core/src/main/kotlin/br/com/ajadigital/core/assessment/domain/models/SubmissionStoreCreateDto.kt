package br.com.ajadigital.core.assessment.domain.models

data class SubmissionStoreCreateDto(val actorId: String, val role: String, val sessionHash: String, val assessmentId: String, val attachments: List<SubmissionAttachment>, val key: String, val requestHash: String)
