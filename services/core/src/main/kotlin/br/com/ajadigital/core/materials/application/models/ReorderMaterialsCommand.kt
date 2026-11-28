package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.MaterialRevision
import java.util.Collections

class ReorderMaterialsCommand(
    val actor: AuthenticatedActor,
    val moduleId: String,
    val expectedRevision: Int,
    items: List<MaterialRevision>,
    val page: Int,
    val size: Int,
) {
    val items: List<MaterialRevision> = Collections.unmodifiableList(items.toList())
}
