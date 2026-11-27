package br.com.ajadigital.core.attendance.application.models

data class AttendanceEligibleStudent(val id: String, val name: String, val eligible: Boolean, val reason: String?)
