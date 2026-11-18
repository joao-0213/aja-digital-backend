package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

data class ContentStoreUploadDto(val actorId: String, val role: String, val sessionHash: String, val key: String, val name: String, val declaredMime: String, val purpose: String, val parentId: String, val bytes: ByteArray)
