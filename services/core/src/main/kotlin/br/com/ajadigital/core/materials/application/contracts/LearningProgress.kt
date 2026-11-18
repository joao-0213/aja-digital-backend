package br.com.ajadigital.core.materials.application.contracts

data class LearningProgress(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val userId: String,
    val materialId: String,
    val completed: Boolean,
    val lastAccess: String,
)
