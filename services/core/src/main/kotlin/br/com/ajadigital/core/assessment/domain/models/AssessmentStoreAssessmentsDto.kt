package br.com.ajadigital.core.assessment.domain.models

data class AssessmentStoreAssessmentsDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val pageSize: Int, val sort: String, val subjectId: String?, val moduleId: String?, val kind: String?, val status: String?)
