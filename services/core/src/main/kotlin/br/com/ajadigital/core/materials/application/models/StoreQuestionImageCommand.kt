package br.com.ajadigital.core.materials.application.models

data class StoreQuestionImageCommand(
    val id: String,
    val questionId: String,
    val name: String,
    val mime: String,
    val content: FileBytes,
)
