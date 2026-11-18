package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.ProgressAction

data class RecordMaterialProgressCommand(val actor: AuthenticatedActor, val materialId: String, val action: ProgressAction?)
