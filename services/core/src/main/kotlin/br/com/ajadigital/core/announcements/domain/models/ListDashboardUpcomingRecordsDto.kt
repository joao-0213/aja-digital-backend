package br.com.ajadigital.core.announcements.domain.models

data class ListDashboardUpcomingRecordsDto(val actorId: String, val role: String, val sessionHash: String, val from: String, val to: String, val size: Int)
