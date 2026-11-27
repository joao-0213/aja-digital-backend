package br.com.ajadigital.core.dashboard.application.models

sealed interface DashboardRead {
    val subjects: List<DashboardSubject>
    val assessments: List<DashboardAssessment>
    val announcements: List<br.com.ajadigital.core.announcements.application.contracts.Announcement>
    val upcoming: List<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent>
    val topics: List<br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic>
    val role: String
}
