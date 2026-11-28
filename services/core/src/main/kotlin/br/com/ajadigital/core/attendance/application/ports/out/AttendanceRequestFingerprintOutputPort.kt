package br.com.ajadigital.core.attendance.application.ports.out

import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft

interface AttendanceRequestFingerprintOutputPort {
    fun create(draft: AttendanceDraft): String
}
