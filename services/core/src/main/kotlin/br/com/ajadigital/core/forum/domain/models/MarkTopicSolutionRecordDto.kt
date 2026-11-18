package br.com.ajadigital.core.forum.domain.models

data class MarkTopicSolutionRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int, val replyId: String?)
