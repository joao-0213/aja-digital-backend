package br.com.ajadigital.core.forum.application.models

data class SaveForumReplyReceiptCommand(
    val actorId: String,
    val topicId: String,
    val key: String,
    val requestHash: String,
    val response: ForumTopicView,
)
