package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.GetFileMetadataUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class GetFileMetadata(private val store: ContentStore) : GetFileMetadataUseCase {
    override fun file(actor: AcademicActor, id: String): Map<String, Any?> =
        store.file(MaterialsSupport.id(id), actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
}
