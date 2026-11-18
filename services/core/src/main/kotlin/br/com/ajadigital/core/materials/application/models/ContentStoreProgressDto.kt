package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

data class ContentStoreProgressDto(val actorId: String, val role: String, val sessionHash: String, val page: Int, val size: Int, val sort: String, val subjectId: String?, val materialId: String?)
