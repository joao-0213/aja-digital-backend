package br.com.ajadigital.core.forum.application.models

data class ForumAttachmentView(
    val id: String, val name: String, val size: Long, val mime: String, val purpose: String, val parentId: String,
    val status: String, val downloadUrl: String,
)
