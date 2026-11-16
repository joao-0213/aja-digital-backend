package br.com.ajadigital.core.forum.domain.models



data class TopicDraft(val classId: String, val subjectId: String, val title: String, val body: String, val tags: List<String>, val targetType: String?, val targetId: String?, val attachmentId: String?)
