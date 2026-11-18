package br.com.ajadigital.core.announcements.domain.models

data class UpdateAnnouncementRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int, val draft: AnnouncementDraft)
