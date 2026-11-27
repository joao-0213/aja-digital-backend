package br.com.ajadigital.core.forum.application.models

data class WithdrawTopicReplyRecord(val actorId: String, val role: String, val sessionHash: String, val id: String, val replyId: String, val revision: Int)
