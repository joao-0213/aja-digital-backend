package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

data class ContentStoreUpdateMaterialDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val expectedRevision: Int, val title: String?, val duration: Double?, val content: String?, val videoUrl: String?)
