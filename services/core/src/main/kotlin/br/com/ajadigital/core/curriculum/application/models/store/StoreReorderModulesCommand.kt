package br.com.ajadigital.core.curriculum.application.models.store

import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision

data class StoreReorderModulesCommand(
    val subjectId: String,
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val expectedRevision: Int,
    val items: List<ModuleRevision>,
    val page: Int,
    val size: Int,
) {
    override fun toString() = "StoreReorderModulesCommand(sessionHash=[redacted])"
}
