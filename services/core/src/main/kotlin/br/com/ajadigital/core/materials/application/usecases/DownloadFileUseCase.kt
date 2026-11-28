package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.DownloadFileQuery
import br.com.ajadigital.core.materials.application.models.DownloadedFile
import br.com.ajadigital.core.materials.application.ports.`in`.DownloadFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class DownloadFileUseCase(
    private val store: FileStoreOutputPort,
) : DownloadFileInputPort {
    override fun download(request: DownloadFileQuery): DownloadedFile {
        MaterialRules.id(request.id)
        request.actor.requireSessionRole()
        return store.download(request) ?: throw MaterialsNotFound()
    }
}
