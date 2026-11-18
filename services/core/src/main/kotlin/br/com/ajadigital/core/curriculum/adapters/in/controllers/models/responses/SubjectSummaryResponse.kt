package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.responses

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.contracts.SubjectTeacher
import com.fasterxml.jackson.annotation.JsonUnwrapped

data class SubjectSummaryResponse(
    @get:JsonUnwrapped val subject: Subject,
    val className: String?,
    val teachers: List<SubjectTeacher>,
    val canManageContent: Boolean,
    val publicationIds: List<String>,
    val hasUpdates: Boolean,
)
