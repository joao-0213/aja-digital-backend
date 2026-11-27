package br.com.ajadigital.core.attendance.application.models

import java.util.Collections

class AttendanceSessionPage(items: List<AttendanceRead>, val page: Int, val pageSize: Int, val total: Int) {
    val items: List<AttendanceRead> = Collections.unmodifiableList(items.toList())
}
