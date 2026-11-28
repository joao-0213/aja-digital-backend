package br.com.ajadigital.core.forum.application.models

data class FindForumReplyReceiptQuery(
    val actorId: String,
    val topicId: String,
    val key: String,
    val requestHash: String,
)
