package br.com.ajadigital.core.curriculum.application.models

data class Module(
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
