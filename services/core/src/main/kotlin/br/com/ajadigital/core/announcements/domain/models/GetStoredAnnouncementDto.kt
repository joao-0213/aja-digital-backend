package br.com.ajadigital.core.announcements.domain.models

data class GetStoredAnnouncementDto(val actorId: String, val role: String, val sessionHash: String, val id: String)
