package br.com.ajadigital.core.announcements.application.ports.out

import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.models.AuthorizeAnnouncementCreate
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementCalendarQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsStoreQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementStoreQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingStoreQuery
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementRecord
import br.com.ajadigital.core.shared.application.contracts.Page

interface AnnouncementStoreOutputPort {
    fun authorizeCreate(request: AuthorizeAnnouncementCreate)

    fun create(request: CreateAnnouncementRecord): Announcement

    fun update(request: UpdateAnnouncementRecord): Announcement?

    fun withdraw(request: WithdrawAnnouncementRecord): Announcement?

    fun announcement(request: GetStoredAnnouncementQuery): Announcement?

    fun announcements(request: ListAnnouncementsStoreQuery): Page<Announcement>

    fun calendar(request: ListAnnouncementCalendarQuery): Page<AnnouncementCalendarEvent>

    fun dashboardAnnouncements(request: ListDashboardAnnouncementStoreQuery): List<Announcement>

    fun dashboardUpcoming(request: ListDashboardUpcomingStoreQuery): List<AnnouncementCalendarEvent>
}
