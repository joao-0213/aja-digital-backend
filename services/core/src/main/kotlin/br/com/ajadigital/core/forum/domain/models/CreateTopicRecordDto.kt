package br.com.ajadigital.core.forum.domain.models

data class CreateTopicRecordDto(val actorId: String, val role: String, val sessionHash: String, val draft: TopicDraft, val idempotencyKey: String, val requestHash: String)
