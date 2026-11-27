package br.com.ajadigital.core.academic.application.models

import java.time.Instant
import java.util.Collections

class AcademicEnrollmentView(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String,
    val classId: String,
    val email: String,
    val studentId: String?,
    val status: String,
    val createdBy: String,
    val enrolledAt: Instant?,
    val endedAt: Instant?,
    val legacy: Boolean,
    unassignedAssessmentIds: List<String>,
    val className: String,
) {
    val unassignedAssessmentIds: List<String> = Collections.unmodifiableList(unassignedAssessmentIds.toList())
}
