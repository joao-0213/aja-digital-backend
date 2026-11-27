package br.com.ajadigital.core.forum.application.models

import java.util.Collections

class ForumTopicView(
    val id: String, val revision: Int, val createdAt: String, val updatedAt: String, val updatedBy: String?,
    val classId: String, val subjectId: String, val title: String, val body: String, tags: List<String>,
    val target: ForumTargetView?, val attachmentId: String?, val author: ForumAuthorView,
    replies: List<ForumReplyView>, val read: Boolean, val attachment: ForumAttachmentView?,
    val legacyReference: String?, val legacyAttachmentName: String?,
) {
    val tags: List<String> = Collections.unmodifiableList(tags.toList())
    val replies: List<ForumReplyView> = Collections.unmodifiableList(replies.toList())

    fun revised(revision: Int, updatedAt: String, updatedBy: String?) = ForumTopicView(
        id, revision, createdAt, updatedAt, updatedBy, classId, subjectId, title, body, tags, target,
        attachmentId, author, replies, read, attachment, legacyReference, legacyAttachmentName,
    )

    fun withDetails(replies: List<ForumReplyView>, read: Boolean, attachment: ForumAttachmentView?) = ForumTopicView(
        id, revision, createdAt, updatedAt, updatedBy, classId, subjectId, title, body, tags, target,
        attachmentId, author, replies, read, attachment, legacyReference, attachment?.name,
    )
}
