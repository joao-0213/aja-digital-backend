package br.com.ajadigital.core.curriculum.domain.models

data class VisitSubjectRecordDto(val subjectId: String, val actorId: String, val role: String, val sessionHash: String, val publicationIds: List<String>?)
