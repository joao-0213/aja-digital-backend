package br.com.ajadigital.core.announcements.domain.models

data class CreateAnnouncementRecordDto(val actorId: String, val role: String, val sessionHash: String, val draft: AnnouncementDraft, val key: String, val requestHash: String)
