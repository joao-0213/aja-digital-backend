package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.DownloadedFile
import br.com.ajadigital.core.materials.domain.ports.`in`.DownloadFileUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class DownloadFile(private val store: ContentStore) : DownloadFileUseCase {
    override fun download(actor: AcademicActor, id: String, disposition: String?): DownloadedFile =
        store.download(MaterialsSupport.id(id), actor.id, actor.role, actor.sessionTokenHash, disposition) ?: throw AcademicNotFound()
}
