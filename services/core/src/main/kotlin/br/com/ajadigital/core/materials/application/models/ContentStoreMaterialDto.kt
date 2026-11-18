package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

data class ContentStoreMaterialDto(val id: String, val actorId: String, val role: String, val sessionHash: String)
