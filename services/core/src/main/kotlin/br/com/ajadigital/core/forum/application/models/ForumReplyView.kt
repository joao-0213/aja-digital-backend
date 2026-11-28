package br.com.ajadigital.core.forum.application.models

data class ForumReplyView(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String?,
    val author: ForumAuthorView,
    val body: String,
    val voteCount: Int,
    val votedByMe: Boolean,
    val accepted: Boolean,
)
