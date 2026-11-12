package br.com.ajadigital.core.ports.out

import java.time.LocalDate
import java.time.LocalTime

data class AttendanceDraft(val subjectId: String, val date: LocalDate, val time: LocalTime, val records: Map<String, Boolean>)

interface AttendanceStore {
    fun authorizeCreate(actorId: String, role: String, sessionHash: String, subjectId: String)
    fun list(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: LocalDate?, to: LocalDate?): AcademicPage
    fun eligible(actorId: String, role: String, sessionHash: String, subjectId: String, date: LocalDate, time: LocalTime, page: Int, size: Int): AcademicPage
    fun attendance(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun create(actorId: String, role: String, sessionHash: String, draft: AttendanceDraft): Map<String, Any?>
    fun update(actorId: String, role: String, sessionHash: String, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?>?
}
