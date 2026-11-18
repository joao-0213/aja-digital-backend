package br.com.ajadigital.core.materials.application.models

data class FileMetadata(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val purpose: FilePurpose,
    val parentId: String,
    val status: FileStatus,
)
