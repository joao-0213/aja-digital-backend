package br.com.ajadigital.core.materials.domain.models

data class ContentStoreMaterialsDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val moduleId: String?, val subjectId: String?)
