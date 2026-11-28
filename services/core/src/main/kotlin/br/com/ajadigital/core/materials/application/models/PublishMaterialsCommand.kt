package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections

class PublishMaterialsCommand(
    val actor: AuthenticatedActor,
    val key: String,
    val moduleId: String,
    val revision: Int,
    items: List<MaterialDraft>,
) {
    val items: List<MaterialDraft> = Collections.unmodifiableList(items.toList())
}
