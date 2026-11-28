package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class StoreUpdateMaterialCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val expectedRevision: Int,
    val title: String?,
    val duration: Double?,
    val content: String?,
    val videoUrl: String?,
)
