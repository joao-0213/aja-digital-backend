package br.com.ajadigital.core.materials.application.models

data class MaterialModule(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val subjectId: String,
    val title: String,
    val description: String,
    val order: Int,
)
