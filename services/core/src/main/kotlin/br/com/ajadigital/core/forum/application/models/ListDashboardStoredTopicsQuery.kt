package br.com.ajadigital.core.forum.application.models

data class ListDashboardStoredTopicsQuery(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val size: Int,
)
