package br.com.ajadigital.core.dashboard.application.models

import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import java.util.Collections

class DashboardStaff(
    subjects: List<DashboardSubject>,
    assessments: List<DashboardAssessment>,
    announcements: List<br.com.ajadigital.core.announcements.application.contracts.Announcement>,
    upcoming: List<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent>,
    topics: List<br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic>,
    override val role: String,
    classes: List<DashboardClassProjection>,
    val pendingEnrollmentCount: Int,
    val studentCount: Int,
    val pendingFeedbackCount: Int,
) : DashboardRead {
    override val subjects = Collections.unmodifiableList(subjects.toList())
    override val assessments = Collections.unmodifiableList(assessments.toList())
    override val announcements = Collections.unmodifiableList(announcements.toList())
    override val upcoming = Collections.unmodifiableList(upcoming.toList())
    override val topics = Collections.unmodifiableList(topics.toList())
    val classes = Collections.unmodifiableList(classes.toList())
}
