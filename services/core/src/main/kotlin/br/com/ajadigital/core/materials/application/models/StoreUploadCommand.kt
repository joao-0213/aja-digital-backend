package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class StoreUploadCommand(
    val actor: AuthenticatedActor,
    val key: String,
    val name: String,
    val mime: String,
    val purpose: UploadPurpose,
    val parentId: String,
    val content: FileBytes,
)
