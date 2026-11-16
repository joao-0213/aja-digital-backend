package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetAnnouncementUseCase {
    fun announcement(actor: AcademicActor, id: String): Map<String, Any?>
}
