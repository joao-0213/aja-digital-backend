package br.com.ajadigital.core.attendance.application.models

import java.util.Collections

class AttendanceEligibleStudentPage(
    items: List<AttendanceEligibleStudent>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
) {
    val items: List<AttendanceEligibleStudent> = Collections.unmodifiableList(items.toList())
}
