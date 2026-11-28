package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class ReorderModulesCommand(
    val actor: AuthenticatedActor,
    val subjectId: String,
    val revision: Int,
    items: List<ModuleRevision>,
    val page: Int,
    val pageSize: Int,
) {
    val items: List<ModuleRevision> = items.toList()
}
