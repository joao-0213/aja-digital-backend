package br.com.ajadigital.core.curriculum.domain.models

data class CreateModuleRecordDto(val actorId: String, val role: String, val sessionHash: String, val subjectId: String, val title: String, val description: String)
