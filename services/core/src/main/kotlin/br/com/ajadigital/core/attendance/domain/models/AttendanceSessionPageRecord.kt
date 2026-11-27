package br.com.ajadigital.core.attendance.domain.models

data class AttendanceSessionPageRecord(val items: List<AttendanceSessionRecord>, val page: Int, val pageSize: Int, val total: Int)
