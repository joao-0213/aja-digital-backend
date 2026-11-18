package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.domain.models.ContentStoreDownloadDto

import br.com.ajadigital.core.materials.application.models.DownloadFileDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.DownloadedFile
import br.com.ajadigital.core.materials.application.ports.`in`.DownloadFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class DownloadFileUseCase(private val store: ContentStoreOutputPort) : DownloadFileInputPort {
    override fun download(request: DownloadFileDto): DownloadedFile = run { val (actor, id, disposition) = request; store.download(ContentStoreDownloadDto(MaterialsPolicy.id(id), actor.id, actor.role, actor.sessionValidationReference, disposition)) ?: throw AcademicNotFound() }
}
