package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.ReorderMaterialsCommand
import br.com.ajadigital.core.materials.application.models.StorePublishMaterialsCommand
import br.com.ajadigital.core.materials.application.models.StoreUpdateMaterialCommand
import br.com.ajadigital.core.shared.application.contracts.Page

/** Revalidate current authorization under the operation's locks. Failure is explicit and rolls back all writes. */
interface MaterialWriteOutputPort {
    fun authorizeMaterialPublication(
        actor: AuthenticatedActor,
        moduleId: String,
    )

    fun publishMaterials(request: StorePublishMaterialsCommand): Page<Material>

    fun updateMaterial(request: StoreUpdateMaterialCommand): Material

    fun reorderMaterials(request: ReorderMaterialsCommand): Page<Material>
}
