package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementDto

interface UpdateAnnouncementInputPort {
    fun update(request: UpdateAnnouncementDto): Map<String, Any?>
}
