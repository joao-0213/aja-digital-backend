package br.com.ajadigital.core.dashboard.application.usecases
import br.com.ajadigital.core.dashboard.application.models.*

import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementsDto
import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingEventsDto
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.CountMyPendingEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListDashboardClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.dashboard.application.ports.`in`.GetDashboardInputPort
import br.com.ajadigital.core.dashboard.application.models.GetDashboardDto
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.forum.application.models.ListDashboardTopicsDto
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
    private val myPendingEnrollments: CountMyPendingEnrollmentsInputPort,
    private val pendingStudentAssessments: CountPendingStudentAssessmentsInputPort,
    private val dashboardClasses: ListDashboardClassesInputPort,
    private val enrollments: ListEnrollmentsInputPort,
    private val students: ListStudentsInputPort,
    private val pendingFeedback: CountPendingFeedbackInputPort,
    private val clock: Clock,
) : GetDashboardInputPort {
    override fun dashboard(request: GetDashboardDto): Map<String, Any?> {
        val (actor) = request
        require(actor.active && actor.role in setOf("student", "teacher", "admin") && (actor.role != "student" || actor.emailConfirmedAt != null)) { throw AcademicForbidden() }
        val subjectsPage = subjects.subjects(br.com.ajadigital.core.curriculum.application.models.ListSubjectsDto(actor, 1, LIMIT, "id", null, null))
        val assessmentPage = dashboardAssessments.dashboardAssessments(br.com.ajadigital.core.assessment.application.models.ListDashboardAssessmentsDto(actor, LIMIT))
        val announcementItems = dashboardAnnouncements.dashboardAnnouncements(ListDashboardAnnouncementsDto(actor, 5))
        val today = LocalDate.ofInstant(clock.instant(), BRASILIA)
        val upcomingItems = dashboardUpcoming.dashboardUpcoming(ListDashboardUpcomingEventsDto(actor, today, today.plusDays(30), 5))
        val topicItems = dashboardTopics.dashboardTopics(ListDashboardTopicsDto(actor))
        val summary = mapOf(
            "subjects" to subjectsPage.items.map { it.project(SUBJECT_FIELDS) },
            "assessments" to items(assessmentPage).map { it.project(ASSESSMENT_FIELDS) },
            "announcements" to announcementItems,
            "upcoming" to upcomingItems,
            "topics" to topicItems,
            "role" to actor.role,
        )

        return if (actor.role == "student") studentDashboard(actor, summary) else staffDashboard(actor, summary)
    }

    private fun studentDashboard(actor: AuthenticatedActor, summary: Map<String, Any?>): Map<String, Any?> {
        val contentProjection = dashboardContent.dashboard(br.com.ajadigital.core.materials.application.models.GetDashboardContentDto(actor))
        return summary + mapOf(
            "resume" to contentProjection.resume,
            "progress" to contentProjection.progress,
            "completedMaterials" to contentProjection.completedMaterials,
            "totalMaterials" to contentProjection.totalMaterials,
            "pendingEnrollmentCount" to myPendingEnrollments.myPendingEnrollmentCount(br.com.ajadigital.core.academic.application.models.CountMyPendingEnrollmentsDto(actor)),
            "pendingAssessmentCount" to pendingStudentAssessments.pendingStudentCount(br.com.ajadigital.core.assessment.application.models.CountPendingStudentAssessmentsDto(actor)),
        )
    }

    private fun staffDashboard(actor: AuthenticatedActor, summary: Map<String, Any?>): Map<String, Any?> {
        val classesPage = dashboardClasses.dashboardClasses(br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto(actor, LIMIT))
        val enrollmentPage = enrollments.enrollments(br.com.ajadigital.core.academic.application.models.ListEnrollmentsDto(actor, 1, 1, "id", null, null, listOf("pending"), null))
        val studentPage = students.students(br.com.ajadigital.core.academic.application.models.ListStudentsDto(actor, 1, 1, "id", null, null, false))
        return summary + mapOf(
            "classes" to classesPage.items.map { it.project(CLASS_FIELDS) },
            "pendingEnrollmentCount" to enrollmentPage.total,
            "studentCount" to studentPage.total,
            "pendingFeedbackCount" to pendingFeedback.pendingFeedbackCount(br.com.ajadigital.core.assessment.application.models.CountPendingFeedbackDto(actor)),
        )
    }

    private fun items(page: Map<String, Any?>) = (page["items"] as? List<*>)?.filterIsInstance<Map<String, Any?>>().orEmpty()
    private fun Map<String, Any?>.project(fields: Set<String>) = filterKeys(fields::contains)

    private companion object {
        const val LIMIT = 100
        val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")
        val SUBJECT_FIELDS = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "classId", "className", "name", "teacherIds", "color", "icon", "description", "teachers", "canManageContent", "hasUpdates")
        val CLASS_FIELDS = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "name", "code", "year", "shift", "active", "studentCount")
        val ASSESSMENT_FIELDS = setOf("id", "revision", "createdAt", "updatedAt", "updatedBy", "moduleId", "title", "description", "kind", "maxGrade", "deadline", "required", "allowLate", "status", "publishedAt", "questionCount", "assigned", "subjectName")
    }
}
