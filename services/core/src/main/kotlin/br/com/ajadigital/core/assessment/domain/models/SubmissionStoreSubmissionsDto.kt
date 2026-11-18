package br.com.ajadigital.core.assessment.domain.models


data class SubmissionStoreSubmissionsDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val pageSize: Int, val sort: String, val assessmentId: String?, val studentId: String?)
