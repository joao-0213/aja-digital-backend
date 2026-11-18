package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.models.*

import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementDto

interface WithdrawAnnouncementInputPort {
    fun withdraw(request: WithdrawAnnouncementDto): Map<String, Any?>
}
