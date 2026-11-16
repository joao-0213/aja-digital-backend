package br.com.ajadigital.core.attendance.domain.models

import java.time.LocalDate
import java.time.LocalTime


data class AttendanceDraft(val subjectId: String, val date: LocalDate, val time: LocalTime, val records: Map<String, Boolean>)
