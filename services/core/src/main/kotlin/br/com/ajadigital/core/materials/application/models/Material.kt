package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.materials.application.contracts.MaterialKind

data class Material(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val moduleId: String,
    val title: String,
    val kind: MaterialKind,
    val duration: Double,
    val content: String,
    val fileId: String?,
    val videoUrl: String?,
    val file: FileMetadata?,
    val author: NamedPerson,
    val order: Int,
) {
    init {
        require(
            when (kind) {
                MaterialKind.LESSON -> fileId == null && file == null && videoUrl == null
                MaterialKind.PDF -> fileId != null && file?.id == fileId && videoUrl == null
                MaterialKind.VIDEO -> fileId == null && file == null // Historical videos may have no URL.
            },
        )
    }
}
