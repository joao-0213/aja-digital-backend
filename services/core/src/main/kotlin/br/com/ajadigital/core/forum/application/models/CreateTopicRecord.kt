package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.forum.domain.models.TopicDraft

data class CreateTopicRecord(val actorId: String, val role: String, val sessionHash: String, val draft: TopicDraft, val idempotencyKey: String, val requestHash: String)
