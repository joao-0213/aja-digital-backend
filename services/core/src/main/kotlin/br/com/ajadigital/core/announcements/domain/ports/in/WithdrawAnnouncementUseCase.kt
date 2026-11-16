package br.com.ajadigital.core.announcements.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface WithdrawAnnouncementUseCase {
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
}
