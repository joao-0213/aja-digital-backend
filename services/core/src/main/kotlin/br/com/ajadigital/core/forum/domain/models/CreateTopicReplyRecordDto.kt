package br.com.ajadigital.core.forum.domain.models

data class CreateTopicReplyRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val body: String)
