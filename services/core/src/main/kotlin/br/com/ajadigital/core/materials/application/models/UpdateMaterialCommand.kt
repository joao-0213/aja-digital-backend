package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange

data class UpdateMaterialCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    val title: FieldChange<String>,
    val duration: FieldChange<Double>,
    val content: FieldChange<String>,
    val videoUrl: FieldChange<String>,
)
