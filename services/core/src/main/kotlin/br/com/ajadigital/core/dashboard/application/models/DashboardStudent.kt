package br.com.ajadigital.core.dashboard.application.models

import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.contracts.ResumeMaterial
import java.util.Collections

class DashboardStudent(
    subjects: List<DashboardSubject>,
    assessments: List<DashboardAssessment>,
    announcements: List<br.com.ajadigital.core.announcements.application.contracts.Announcement>,
    upcoming: List<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent>,
    topics: List<br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic>,
    override val role: String,
    val resume: ResumeMaterial?,
    progress: List<LearningProgress>,
    val completedMaterials: Int,
    val totalMaterials: Int,
    val pendingEnrollmentCount: Int,
    val pendingAssessmentCount: Int,
) : DashboardRead {
    override val subjects = Collections.unmodifiableList(subjects.toList())
    override val assessments = Collections.unmodifiableList(assessments.toList())
    override val announcements = Collections.unmodifiableList(announcements.toList())
    override val upcoming = Collections.unmodifiableList(upcoming.toList())
    override val topics = Collections.unmodifiableList(topics.toList())
    val progress = Collections.unmodifiableList(progress.toList())
}
