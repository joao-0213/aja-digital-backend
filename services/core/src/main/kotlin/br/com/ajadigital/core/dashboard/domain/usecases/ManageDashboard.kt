package br.com.ajadigital.core.dashboard.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.usecases.ManageAcademics
import br.com.ajadigital.core.announcements.domain.usecases.ManageAnnouncements
import br.com.ajadigital.core.assessment.domain.usecases.ManageAssessments
import br.com.ajadigital.core.materials.domain.usecases.ManageContent
import br.com.ajadigital.core.curriculum.domain.usecases.ManageSubjects
import br.com.ajadigital.core.assessment.domain.usecases.ManageSubmissions
import br.com.ajadigital.core.forum.domain.usecases.ManageTopics

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ManageDashboard(
    private val academics: ManageAcademics,
    private val subjects: ManageSubjects,
    private val content: ManageContent,
    private val assessments: ManageAssessments,
    private val submissions: ManageSubmissions,
    private val announcements: ManageAnnouncements,
    private val topics: ManageTopics,
    private val clock: Clock,
) {
    fun dashboard(actor: AcademicActor): Map<String, Any?> {
        require(actor.active && actor.role in setOf("student", "teacher", "admin") && (actor.role != "student" || actor.emailConfirmedAt != null)) { throw AcademicForbidden() }
        val subjectsPage = subjects.subjects(actor, 1, LIMIT, "id", null, null)
        val assessmentPage = assessments.dashboardAssessments(actor, LIMIT)
        val announcementItems = announcements.dashboardAnnouncements(actor, 5)
        val today = LocalDate.ofInstant(clock.instant(), BRASILIA)
        val upcomingItems = announcements.dashboardUpcoming(actor, today, today.plusDays(30), 5)
        val topicItems = topics.dashboardTopics(actor)
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
        val contentProjection = content.dashboard(actor)
        return summary + mapOf(
            "resume" to contentProjection.resume,
            "progress" to contentProjection.progress,
            "completedMaterials" to contentProjection.completedMaterials,
            "totalMaterials" to contentProjection.totalMaterials,
            "pendingEnrollmentCount" to academics.myPendingEnrollmentCount(actor),
            "pendingAssessmentCount" to assessments.pendingStudentCount(actor),
        )
    }

    private fun staffDashboard(actor: AcademicActor, summary: Map<String, Any?>): Map<String, Any?> {
        val classesPage = academics.dashboardClasses(actor, LIMIT)
        val enrollmentPage = academics.enrollments(actor, 1, 1, "id", null, null, listOf("pending"), null)
        val studentPage = academics.students(actor, 1, 1, "id", null, null, false)
        return summary + mapOf(
            "classes" to classesPage.items.map { it.project(CLASS_FIELDS) },
            "pendingEnrollmentCount" to enrollmentPage.total,
            "studentCount" to studentPage.total,
            "pendingFeedbackCount" to submissions.pendingFeedbackCount(actor),
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
