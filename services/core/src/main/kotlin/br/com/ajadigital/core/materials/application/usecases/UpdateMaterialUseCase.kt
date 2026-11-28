package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.StoreUpdateMaterialCommand
import br.com.ajadigital.core.materials.application.models.UpdateMaterialCommand
import br.com.ajadigital.core.materials.application.ports.`in`.UpdateMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.FieldChange

class UpdateMaterialUseCase(
    private val store: MaterialWriteOutputPort,
) : UpdateMaterialInputPort {
    override fun update(request: UpdateMaterialCommand): Material {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val titleChange = request.title
        val durationChange = request.duration
        val contentChange = request.content
        val videoUrlChange = request.videoUrl
        actor.requireWriter()
        val title = titleChange.valueOrNull()
        val duration = durationChange.valueOrNull()
        val content = contentChange.valueOrNull()
        val videoUrl = videoUrlChange.valueOrNull()
        require(
            revision > 0 && (title == null || title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) &&
                (duration == null || duration in 1.0..MAX_DURATION_MINUTES) && (content == null || content.length <= MAX_CONTENT_LENGTH) &&
                (videoUrl == null || NewMaterial.Source.Video.isValidUrl(videoUrl)),
        )
        return store.updateMaterial(
            StoreUpdateMaterialCommand(actor, MaterialRules.id(id), revision, title?.trim(), duration, content, videoUrl),
        )
    }
}

private fun <T> FieldChange<T>.valueOrNull(): T? = when (this) {
    FieldChange.Unchanged -> null
    is FieldChange.Set -> value
}

private const val MAX_TITLE_LENGTH = 160
private const val MAX_DURATION_MINUTES = 600.0
private const val MAX_CONTENT_LENGTH = 1_000_000
