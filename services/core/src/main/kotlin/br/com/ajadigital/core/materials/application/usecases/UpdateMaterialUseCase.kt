package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.StoreUpdateMaterialCommand
import br.com.ajadigital.core.materials.application.models.UpdateMaterialCommand
import br.com.ajadigital.core.materials.application.ports.`in`.UpdateMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.FieldChange

class UpdateMaterialUseCase(private val store: MaterialWriteOutputPort) : UpdateMaterialInputPort {
    override fun update(request: UpdateMaterialCommand): Material {
        val (actor, id, revision, titleChange, durationChange, contentChange, videoUrlChange) = request
        actor.requireWriter()
        val title = titleChange.valueOrNull()
        val duration = durationChange.valueOrNull()
        val content = contentChange.valueOrNull()
        val videoUrl = videoUrlChange.valueOrNull()
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) &&
            (duration == null || duration in 1.0..600.0) && (content == null || content.length <= 1_000_000) &&
            (videoUrl == null || NewMaterial.Source.Video.isValidUrl(videoUrl)))
        return store.updateMaterial(StoreUpdateMaterialCommand(actor, MaterialRules.id(id), revision, title?.trim(), duration, content, videoUrl))
    }
}

private fun <T> FieldChange<T>.valueOrNull(): T? = when (this) {
    FieldChange.Unchanged -> null
    is FieldChange.Set -> value
}
