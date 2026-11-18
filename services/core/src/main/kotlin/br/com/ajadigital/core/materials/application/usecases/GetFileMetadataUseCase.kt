package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.GetFileMetadataQuery
import br.com.ajadigital.core.materials.application.ports.`in`.GetFileMetadataInputPort
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class GetFileMetadataUseCase(private val store: FileStoreOutputPort) : GetFileMetadataInputPort {
    override fun file(request: GetFileMetadataQuery): FileMetadata {
        MaterialRules.id(request.id)
        request.actor.requireSessionRole()
        return store.file(request) ?: throw MaterialsNotFound()
    }
}
