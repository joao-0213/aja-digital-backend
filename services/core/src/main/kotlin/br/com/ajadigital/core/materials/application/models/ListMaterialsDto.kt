package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.AcademicPage

data class ListMaterialsDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val moduleId: String?, val subjectId: String?)
