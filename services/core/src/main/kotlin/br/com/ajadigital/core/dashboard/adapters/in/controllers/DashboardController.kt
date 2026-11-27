package br.com.ajadigital.core.dashboard.adapters.`in`.controllers
import br.com.ajadigital.core.dashboard.application.models.*

import br.com.ajadigital.core.dashboard.application.ports.`in`.GetDashboardInputPort
import br.com.ajadigital.core.dashboard.application.models.DashboardRead
import br.com.ajadigital.core.dashboard.application.models.DashboardStudent
import br.com.ajadigital.core.dashboard.application.models.DashboardStaff
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(private val auth: ResolveAuthenticatedActorInputPort, private val dashboard: GetDashboardInputPort) {
    @GetMapping("/api/v1/me/dashboard")
    fun dashboard(@CookieValue(name = "aja_session", required = false) cookie: String?) = project(dashboard.dashboard(auth.resolve(cookie)))

    private fun project(read: DashboardRead): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(
            "subjects" to read.subjects.map { s -> linkedMapOf(
                "id" to s.id, "revision" to s.revision, "createdAt" to s.createdAt, "updatedAt" to s.updatedAt,
                "updatedBy" to s.updatedBy, "classId" to s.classId, "name" to s.name, "teacherIds" to s.teacherIds,
                "color" to s.color, "icon" to s.icon, "description" to s.description, "className" to s.className,
                "teachers" to s.teachers, "canManageContent" to s.canManageContent, "hasUpdates" to s.hasUpdates,
            ) },
            "assessments" to read.assessments.map { a -> linkedMapOf<String, Any?>(
                "id" to a.id, "revision" to a.revision, "createdAt" to a.createdAt, "updatedAt" to a.updatedAt,
                "updatedBy" to a.updatedBy, "moduleId" to a.moduleId, "title" to a.title, "description" to a.description,
                "kind" to a.kind, "maxGrade" to a.maxGrade, "deadline" to a.deadline, "required" to a.required,
                "allowLate" to a.allowLate, "status" to a.status, "publishedAt" to a.publishedAt,
                "questionCount" to a.questionCount,
            ).apply { a.assigned?.let { put("assigned", it) } } },
            "announcements" to read.announcements, "upcoming" to read.upcoming, "topics" to read.topics, "role" to read.role,
        )
        when (read) {
            is DashboardStudent -> {
                result["resume"] = read.resume?.let { mapOf("id" to it.id, "title" to it.title, "moduleId" to it.moduleId, "kind" to it.kind.value) }
                result["progress"] = read.progress
                result["completedMaterials"] = read.completedMaterials
                result["totalMaterials"] = read.totalMaterials
                result["pendingEnrollmentCount"] = read.pendingEnrollmentCount
                result["pendingAssessmentCount"] = read.pendingAssessmentCount
            }
            is DashboardStaff -> {
                result["classes"] = read.classes
                result["pendingEnrollmentCount"] = read.pendingEnrollmentCount
                result["studentCount"] = read.studentCount
                result["pendingFeedbackCount"] = read.pendingFeedbackCount
            }
        }
        return result
    }
}
