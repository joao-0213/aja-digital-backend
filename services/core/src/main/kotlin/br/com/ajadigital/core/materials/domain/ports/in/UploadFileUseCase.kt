package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UploadFileUseCase {
    fun upload(actor: AcademicActor, key: String, name: String, mime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?>
}
