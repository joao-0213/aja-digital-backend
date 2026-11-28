package br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = false)
data class TopicCreateRequest(val classId: String, val subjectId: String, val title: String, val body: String, val tags: List<String>, val target: TopicTargetRequest?, val attachmentId: String?)
