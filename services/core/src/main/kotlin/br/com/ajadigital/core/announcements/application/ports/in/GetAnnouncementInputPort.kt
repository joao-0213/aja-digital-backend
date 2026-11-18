package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.GetAnnouncementDto

interface GetAnnouncementInputPort {
    fun announcement(request: GetAnnouncementDto): Map<String, Any?>
}
