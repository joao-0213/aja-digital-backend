package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.MaterialCreate

interface PublishMaterialsUseCase {
    fun publish(actor: AcademicActor, key: String, moduleId: String, revision: Int, items: List<MaterialCreate>): Map<String, Any?>
}
