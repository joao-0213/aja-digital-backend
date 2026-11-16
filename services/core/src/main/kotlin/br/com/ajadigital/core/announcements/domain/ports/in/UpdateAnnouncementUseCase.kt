package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateAnnouncementUseCase {
    fun update(actor: AcademicActor, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?>
}
