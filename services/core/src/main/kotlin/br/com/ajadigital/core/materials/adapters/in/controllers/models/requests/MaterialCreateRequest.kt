package br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests

import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.MaterialDraft
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialCreateRequest(val moduleId: String, val title: String, val kind: String, val duration: Double, val content: String, val fileId: String? = null, val videoUrl: String? = null) {
    fun command() = MaterialDraft(moduleId, title, MaterialKind.parse(kind), duration, content, fileId, videoUrl)
}
