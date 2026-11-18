package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreUpdateMaterialDto

import br.com.ajadigital.core.materials.application.models.UpdateMaterialDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.ports.`in`.UpdateMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort

class UpdateMaterialUseCase(private val store: ContentStoreOutputPort) : UpdateMaterialInputPort {
    override fun update(request: UpdateMaterialDto): Map<String, Any?> {
        val (actor, id, revision, title, duration, content, videoUrl) = request
                MaterialsPolicy.writer(actor)
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (duration == null || duration in 1.0..600.0) && (content == null || content.length <= 1_000_000) && (videoUrl == null || MaterialsPolicy.validVideoUrl(videoUrl)))
        return store.updateMaterial(ContentStoreUpdateMaterialDto(actor.id, actor.role, actor.sessionValidationReference, MaterialsPolicy.id(id), revision, title?.trim(), duration, content, videoUrl)) ?: throw AcademicConflict()
    }
}
