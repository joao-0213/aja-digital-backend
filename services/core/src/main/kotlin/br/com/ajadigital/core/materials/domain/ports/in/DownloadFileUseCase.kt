package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.DownloadedFile

interface DownloadFileUseCase {
    fun download(actor: AcademicActor, id: String, disposition: String?): DownloadedFile
}
