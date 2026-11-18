package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementDto

interface CreateAnnouncementInputPort {
    fun create(request: CreateAnnouncementDto): Map<String, Any?>
}
