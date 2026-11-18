package br.com.ajadigital.core.announcements.domain.ports.out
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.models.AuthorizeAnnouncementCreateDto
import br.com.ajadigital.core.announcements.domain.models.CreateAnnouncementRecordDto
import br.com.ajadigital.core.announcements.domain.models.UpdateAnnouncementRecordDto
import br.com.ajadigital.core.announcements.domain.models.WithdrawAnnouncementRecordDto
import br.com.ajadigital.core.announcements.domain.models.GetStoredAnnouncementDto
import br.com.ajadigital.core.announcements.domain.models.ListAnnouncementsRecordDto
import br.com.ajadigital.core.announcements.domain.models.ListAnnouncementCalendarDto
import br.com.ajadigital.core.announcements.domain.models.ListDashboardAnnouncementRecordsDto
import br.com.ajadigital.core.announcements.domain.models.ListDashboardUpcomingRecordsDto



interface AnnouncementStoreOutputPort {
    fun authorizeCreate(request: AuthorizeAnnouncementCreateDto)
    fun create(request: CreateAnnouncementRecordDto): Map<String, Any?>
    fun update(request: UpdateAnnouncementRecordDto): Map<String, Any?>?
    fun withdraw(request: WithdrawAnnouncementRecordDto): Map<String, Any?>?
    fun announcement(request: GetStoredAnnouncementDto): Map<String, Any?>?
    fun announcements(request: ListAnnouncementsRecordDto): AcademicPage
    fun calendar(request: ListAnnouncementCalendarDto): AcademicPage
    fun dashboardAnnouncements(request: ListDashboardAnnouncementRecordsDto): List<Map<String, Any?>>
    fun dashboardUpcoming(request: ListDashboardUpcomingRecordsDto): List<Map<String, Any?>>
}
