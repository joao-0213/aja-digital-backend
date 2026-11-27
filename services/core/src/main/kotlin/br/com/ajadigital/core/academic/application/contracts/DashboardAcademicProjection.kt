package br.com.ajadigital.core.academic.application.contracts

import java.time.Instant

class DashboardAcademicProjection(
    classes: List<DashboardClassProjection>,
    val pendingEnrollmentCount: Int,
    val studentCount: Int,
) {
    val classes: List<DashboardClassProjection> = java.util.Collections.unmodifiableList(classes.toList())
}
