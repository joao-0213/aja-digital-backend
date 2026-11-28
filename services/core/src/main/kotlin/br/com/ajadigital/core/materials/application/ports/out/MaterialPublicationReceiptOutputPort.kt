package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.StorePublishMaterialsCommand
import br.com.ajadigital.core.shared.application.contracts.Page

interface MaterialPublicationReceiptOutputPort {
    fun fingerprint(request: StorePublishMaterialsCommand): String

    fun encode(result: Page<Material>): String

    fun decode(response: String): Page<Material>
}
