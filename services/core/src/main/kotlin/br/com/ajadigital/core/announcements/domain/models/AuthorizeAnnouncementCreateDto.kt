package br.com.ajadigital.core.announcements.domain.models

data class AuthorizeAnnouncementCreateDto(val actorId: String, val role: String, val sessionHash: String, val draft: AnnouncementDraft)
