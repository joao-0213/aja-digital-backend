package br.com.ajadigital.core.dashboard.domain.usecases

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardUpcomingEventsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingStudentAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListDashboardAssessmentsUseCase
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CountMyPendingEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListDashboardClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectsUseCase
import br.com.ajadigital.core.dashboard.domain.ports.`in`.GetDashboardUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.ListDashboardTopicsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetDashboardContentUseCase
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class GetDashboard(
    private val subjects: ListSubjectsUseCase,
    private val dashboardAssessments: ListDashboardAssessmentsUseCase,
    private val dashboardAnnouncements: ListDashboardAnnouncementsUseCase,
    private val dashboardUpcoming: ListDashboardUpcomingEventsUseCase,
    private val dashboardTopics: ListDashboardTopicsUseCase,
    private val dashboardContent: GetDashboardContentUseCase,
    private val myPendingEnrollments: CountMyPendingEnrollmentsUseCase,
    private val pendingStudentAssessments: CountPendingStudentAssessmentsUseCase,
    private val dashboardClasses: ListDashboardClassesUseCase,
    private val enrollments: ListEnrollmentsUseCase,
    private val students: ListStudentsUseCase,
    private val pendingFeedback: CountPendingFeedbackUseCase,
    private val clock: Clock,
) : GetDashboardUseCase {
    override fun dashboard(actor: AcademicActor): Map<String, Any?> {
        require(actor.active && actor.role in setOf("student", "teacher", "admin") && (actor.role != "student" || actor.emailConfirmedAt != null)) { throw AcademicForbidden() }
        val subjectsPage = subjects.subjects(actor, 1, LIMIT, "id", null, null)
        val assessmentPage = dashboardAssessments.dashboardAssessments(actor, LIMIT)
        val announcementItems = dashboardAnnouncements.dashboardAnnouncements(actor, 5)
        val today = LocalDate.ofInstant(clock.instant(), BRASILIA)
        val upcomingItems = dashboardUpcoming.dashboardUpcoming(actor, today, today.plusDays(30), 5)
        val topicItems = dashboardTopics.dashboardTopics(actor)
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

    private fun studentDashboard(actor: AcademicActor, summary: Map<String, Any?>): Map<String, Any?> {
        val contentProjection = dashboardContent.dashboard(actor)
        return summary + mapOf(
            "resume" to contentProjection.resume,
            "progress" to contentProjection.progress,
            "completedMaterials" to contentProjection.completedMaterials,
            "totalMaterials" to contentProjection.totalMaterials,
            "pendingEnrollmentCount" to myPendingEnrollments.myPendingEnrollmentCount(actor),
            "pendingAssessmentCount" to pendingStudentAssessments.pendingStudentCount(actor),
        )
    }

    private fun staffDashboard(actor: AcademicActor, summary: Map<String, Any?>): Map<String, Any?> {
        val classesPage = dashboardClasses.dashboardClasses(actor, LIMIT)
        val enrollmentPage = enrollments.enrollments(actor, 1, 1, "id", null, null, listOf("pending"), null)
        val studentPage = students.students(actor, 1, 1, "id", null, null, false)
        return summary + mapOf(
            "classes" to classesPage.items.map { it.project(CLASS_FIELDS) },
            "pendingEnrollmentCount" to enrollmentPage.total,
            "studentCount" to studentPage.total,
            "pendingFeedbackCount" to pendingFeedback.pendingFeedbackCount(actor),
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
