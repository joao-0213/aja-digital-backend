package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class UploadFileCommand(
    val actor: AuthenticatedActor,
    val key: String,
    val name: String,
    val mime: String,
    val purpose: UploadPurpose?,
    val parentId: String,
    bytes: ByteArray,
) {
    internal val content = FileBytes(bytes)
}
