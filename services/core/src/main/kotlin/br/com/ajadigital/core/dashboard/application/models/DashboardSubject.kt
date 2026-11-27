package br.com.ajadigital.core.dashboard.application.models

import br.com.ajadigital.core.curriculum.application.contracts.SubjectTeacher
import java.util.Collections

class DashboardSubject(
    val id: String, val revision: Int, val createdAt: String, val updatedAt: String, val updatedBy: String,
    val classId: String, val className: String?, val name: String, teacherIds: List<String>,
    val color: String, val icon: String, val description: String, teachers: List<SubjectTeacher>,
    val canManageContent: Boolean, val hasUpdates: Boolean,
) {
    val teacherIds = Collections.unmodifiableList(teacherIds.toList())
    val teachers = Collections.unmodifiableList(teachers.toList())
}
