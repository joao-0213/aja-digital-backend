package br.com.ajadigital.core.forum.application.models

data class MarkTopicReadRecord(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val id: String,
)
