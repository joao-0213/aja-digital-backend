package br.com.ajadigital.core.forum.adapters.`in`.controllers

import br.com.ajadigital.core.forum.application.models.ForumTopicView
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import org.springframework.boot.jackson.JacksonMixin

@JacksonMixin(ForumTopicView::class)
@JsonPropertyOrder(
    "id", "revision", "createdAt", "updatedAt", "updatedBy", "classId", "subjectId", "title", "body", "tags",
    "target", "attachmentId", "author", "replies", "read", "attachment", "legacyReference", "legacyAttachmentName",
)
abstract class ForumTopicViewJsonMixin
