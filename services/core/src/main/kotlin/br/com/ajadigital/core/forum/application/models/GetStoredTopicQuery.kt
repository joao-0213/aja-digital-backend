package br.com.ajadigital.core.forum.application.models

data class GetStoredTopicQuery(val actorId: String, val role: String, val sessionHash: String, val id: String)
