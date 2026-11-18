package br.com.ajadigital.core.forum.domain.models

data class WithdrawTopicReplyRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val replyId: String, val revision: Int)
