package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import java.util.Collections

class StorePublishMaterialsCommand(
    val actor: AuthenticatedActor,
    val moduleId: String,
    val expectedRevision: Int,
    items: List<NewMaterial>,
) {
    val items: List<NewMaterial> = Collections.unmodifiableList(items.toList())
}
