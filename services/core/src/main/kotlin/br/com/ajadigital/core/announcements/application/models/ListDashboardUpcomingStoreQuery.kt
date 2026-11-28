package br.com.ajadigital.core.announcements.application.models

data class ListDashboardUpcomingStoreQuery(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val from: String,
    val to: String,
    val size: Int,
)
