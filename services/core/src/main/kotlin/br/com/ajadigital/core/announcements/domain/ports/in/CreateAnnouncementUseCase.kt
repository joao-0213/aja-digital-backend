package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateAnnouncementUseCase {
    fun create(actor: AcademicActor, draft: AnnouncementDraft, key: String): Map<String, Any?>
}
