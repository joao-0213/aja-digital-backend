package br.com.ajadigital.core.forum.application.models

data class MarkTopicSolutionRecord(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val id: String,
    val revision: Int,
    val replyId: String?,
)
