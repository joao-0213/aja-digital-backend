package br.com.ajadigital.core.forum.domain.models

data class AuthorizeTopicReplyDto(val actorId: String, val role: String, val sessionHash: String, val id: String)
