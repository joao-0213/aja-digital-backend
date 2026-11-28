package br.com.ajadigital.core.announcements.application.ports.out

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft

interface AnnouncementRequestFingerprintOutputPort {
    fun create(draft: AnnouncementDraft): String
}
