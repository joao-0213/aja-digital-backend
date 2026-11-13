package br.com.ajadigital.core

import br.com.ajadigital.core.ports.out.MaterialCreate
import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialCreateRequest(val moduleId: String, val title: String, val kind: String, val duration: Double, val content: String, val fileId: String? = null, val videoUrl: String? = null) {
    fun command() = MaterialCreate(moduleId, title, kind, duration, content, fileId, videoUrl)
}
