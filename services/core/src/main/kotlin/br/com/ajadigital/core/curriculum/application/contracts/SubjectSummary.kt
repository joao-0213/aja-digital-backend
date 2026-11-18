package br.com.ajadigital.core.curriculum.application.contracts

import java.util.Collections

class SubjectSummary(
    val subject: Subject,
    val className: String?,
    teachers: List<SubjectTeacher>,
    val canManageContent: Boolean,
    publicationIds: List<String>,
    val hasUpdates: Boolean,
) {
    val teachers: List<SubjectTeacher> = Collections.unmodifiableList(teachers.toList())
    val publicationIds: List<String> = Collections.unmodifiableList(publicationIds.toList())
}
