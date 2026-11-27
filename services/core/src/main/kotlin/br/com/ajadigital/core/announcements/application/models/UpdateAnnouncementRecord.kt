package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft

data class UpdateAnnouncementRecord(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int, val draft: AnnouncementDraft)
