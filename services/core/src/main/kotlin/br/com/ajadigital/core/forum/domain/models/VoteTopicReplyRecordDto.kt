package br.com.ajadigital.core.forum.domain.models

data class VoteTopicReplyRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val replyId: String, val voted: Boolean)
