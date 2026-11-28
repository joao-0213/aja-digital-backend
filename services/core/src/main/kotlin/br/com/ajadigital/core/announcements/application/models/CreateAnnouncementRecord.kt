package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft

data class CreateAnnouncementRecord(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val draft: AnnouncementDraft,
    val key: String,
    val requestHash: String,
)
