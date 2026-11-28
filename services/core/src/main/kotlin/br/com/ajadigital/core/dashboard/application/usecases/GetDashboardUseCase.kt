package br.com.ajadigital.core.dashboard.application.usecases

import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicQuery
import br.com.ajadigital.core.academic.application.ports.`in`.GetDashboardAcademicInputPort
import br.com.ajadigital.core.announcements.application.contracts.ListDashboardAnnouncementsQuery
import br.com.ajadigital.core.announcements.application.contracts.ListDashboardUpcomingEventsQuery
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.assessment.application.contracts.CountPendingFeedbackQuery
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.curriculum.application.contracts.ListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.dashboard.application.models.DashboardAssessment
import br.com.ajadigital.core.dashboard.application.models.DashboardRead
import br.com.ajadigital.core.dashboard.application.models.DashboardStaff
import br.com.ajadigital.core.dashboard.application.models.DashboardStudent
import br.com.ajadigital.core.dashboard.application.models.DashboardSubject
import br.com.ajadigital.core.dashboard.application.ports.`in`.GetDashboardInputPort
import br.com.ajadigital.core.dashboard.domain.exceptions.DashboardForbidden
import br.com.ajadigital.core.forum.application.contracts.ListDashboardTopicsQuery
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.GetDashboardContentInputPort
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class GetDashboardUseCase(
    private val subjects: ListSubjectsInputPort,
    private val dashboardAssessments: ListDashboardAssessmentsInputPort,
    private val dashboardAnnouncements: ListDashboardAnnouncementsInputPort,
    private val dashboardUpcoming: ListDashboardUpcomingEventsInputPort,
    private val dashboardTopics: ListDashboardTopicsInputPort,
    private val dashboardContent: GetDashboardContentInputPort,
    private val pendingStudentAssessments: CountPendingStudentAssessmentsInputPort,
    private val dashboardAcademic: GetDashboardAcademicInputPort,
    private val pendingFeedback: CountPendingFeedbackInputPort,
    private val clock: Clock,
) : GetDashboardInputPort {
    override fun dashboard(actor: AuthenticatedActor): DashboardRead {
        require(actor.active && actor.role in setOf("student", "teacher", "admin") && (actor.role != "student" || actor.emailConfirmedAt != null)) { throw DashboardForbidden() }
        val subjectsPage = subjects.subjects(ListSubjectsQuery(actor, 1, LIMIT, "id", null, null))
        val assessmentItems = dashboardAssessments.dashboardAssessments(ListDashboardAssessmentsQuery(actor, LIMIT))
        val announcementItems = dashboardAnnouncements.dashboardAnnouncements(ListDashboardAnnouncementsQuery(actor, 5))
        val today = LocalDate.ofInstant(clock.instant(), BRASILIA)
        val upcomingItems = dashboardUpcoming.dashboardUpcoming(ListDashboardUpcomingEventsQuery(actor, today, today.plusDays(30), 5))
        val topicItems = dashboardTopics.dashboardTopics(ListDashboardTopicsQuery(actor))
        val summary = Summary(
            subjectsPage.items.map(::subjectProjection),
            assessmentItems.map(::assessmentProjection),
            announcementItems,
            upcomingItems,
            topicItems,
            actor.role,
        )

        return if (actor.role == "student") studentDashboard(actor, summary) else staffDashboard(actor, summary)
    }

    private fun studentDashboard(actor: AuthenticatedActor, summary: Summary): DashboardStudent {
        val contentProjection = dashboardContent.dashboard(actor)
        return DashboardStudent(
            summary.subjects, summary.assessments, summary.announcements, summary.upcoming, summary.topics, summary.role,
            contentProjection.resume, contentProjection.progress, contentProjection.completedMaterials, contentProjection.totalMaterials,
            dashboardAcademic.dashboard(DashboardAcademicQuery(actor)).pendingEnrollmentCount,
            pendingStudentAssessments.pendingStudentCount(actor),
        )
    }

    private fun staffDashboard(actor: AuthenticatedActor, summary: Summary): DashboardStaff {
        val academic = dashboardAcademic.dashboard(DashboardAcademicQuery(actor))
        return DashboardStaff(
            summary.subjects, summary.assessments, summary.announcements, summary.upcoming, summary.topics, summary.role,
            academic.classes, academic.pendingEnrollmentCount, academic.studentCount,
            pendingFeedback.pendingFeedbackCount(CountPendingFeedbackQuery(actor)),
        )
    }

    private fun subjectProjection(summary: br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary): DashboardSubject {
        val subject = summary.subject
        return DashboardSubject(
            subject.id, subject.revision, subject.createdAt, subject.updatedAt, subject.updatedBy, subject.classId,
            summary.className, subject.name, subject.teacherIds, subject.color, subject.icon, subject.description,
            summary.teachers, summary.canManageContent, summary.hasUpdates,
        )
    }

    private fun assessmentProjection(assessment: br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment) = DashboardAssessment(
        assessment.id, assessment.revision, assessment.createdAt.toString(), assessment.updatedAt.toString(), assessment.updatedBy,
        assessment.moduleId, assessment.title, assessment.description, assessment.kind, assessment.maxGrade,
        assessment.deadline?.toString(), assessment.required, assessment.allowLate, "published", assessment.publishedAt.toString(),
        assessment.questionCount, assessment.assigned,
    )

    private data class Summary(
        val subjects: List<DashboardSubject>,
        val assessments: List<DashboardAssessment>,
        val announcements: List<br.com.ajadigital.core.announcements.application.contracts.Announcement>,
        val upcoming: List<br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent>,
        val topics: List<br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic>,
        val role: String,
    )

    private companion object {
        const val LIMIT = 100
        val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
