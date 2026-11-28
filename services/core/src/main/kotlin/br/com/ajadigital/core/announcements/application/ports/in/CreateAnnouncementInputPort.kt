package br.com.ajadigital.core.announcements.application.ports.`in`
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementCommand

interface CreateAnnouncementInputPort {
    fun create(request: CreateAnnouncementCommand): Announcement
}
