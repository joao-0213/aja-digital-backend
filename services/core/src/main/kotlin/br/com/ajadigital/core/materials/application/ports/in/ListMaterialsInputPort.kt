package br.com.ajadigital.core.materials.application.ports.`in`
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ListMaterialsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListMaterialsInputPort {
    fun materials(request: ListMaterialsDto): AcademicPage
}
