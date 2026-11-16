package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetFileMetadataUseCase {
    fun file(actor: AcademicActor, id: String): Map<String, Any?>
}
