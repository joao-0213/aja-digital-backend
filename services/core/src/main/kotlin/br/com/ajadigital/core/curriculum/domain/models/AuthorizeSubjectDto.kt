package br.com.ajadigital.core.curriculum.domain.models

data class AuthorizeSubjectDto(val actorId: String, val role: String, val sessionHash: String, val subjectId: String?)
