package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.application.contracts.MaterialKind

data class MaterialDraft(
    val moduleId: String,
    val title: String,
    val kind: MaterialKind?,
    val duration: Double,
    val content: String,
    val fileId: String?,
    val videoUrl: String?,
)
