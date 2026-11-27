package br.com.ajadigital.core.announcements.application.models

data class GetStoredAnnouncementQuery(val actorId: String, val role: String, val sessionHash: String, val id: String)
