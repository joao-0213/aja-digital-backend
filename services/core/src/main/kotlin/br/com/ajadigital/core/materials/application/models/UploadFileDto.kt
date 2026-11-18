package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UploadFileDto(val actor: AuthenticatedActor, val key: String, val name: String, val mime: String, val purpose: String, val parentId: String, val bytes: ByteArray)
