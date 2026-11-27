package br.com.ajadigital.core.academic.application.models

import java.util.Collections

class AcademicStudentView(
    val student: AcademicStudentSummaryView,
    enrollments: List<AcademicEnrollmentView>,
) {
    val enrollments = Collections.unmodifiableList(enrollments.toList())
}
