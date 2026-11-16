package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.UpdateMaterialUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class UpdateMaterial(private val store: ContentStore) : UpdateMaterialUseCase {
    override fun update(actor: AcademicActor, id: String, revision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?> {
        MaterialsSupport.writer(actor)
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (duration == null || duration in 1.0..600.0) && (content == null || content.length <= 1_000_000) && (videoUrl == null || MaterialsSupport.validVideoUrl(videoUrl)))
        return store.updateMaterial(actor.id, actor.role, actor.sessionTokenHash, MaterialsSupport.id(id), revision, title?.trim(), duration, content, videoUrl) ?: throw AcademicConflict()
    }
}
