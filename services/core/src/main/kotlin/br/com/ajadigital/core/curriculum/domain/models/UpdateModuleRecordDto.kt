package br.com.ajadigital.core.curriculum.domain.models

data class UpdateModuleRecordDto(val id: String, val actorId: String, val role: String, val sessionHash: String, val expectedRevision: Int, val title: String?, val description: String?)
