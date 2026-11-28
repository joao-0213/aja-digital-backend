package br.com.ajadigital.core.curriculum.application.models.store

data class StoreCreateModuleCommand(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val subjectId: String,
    val title: String,
    val description: String,
) {
    override fun toString() = "StoreCreateModuleCommand(sessionHash=[redacted])"
}
