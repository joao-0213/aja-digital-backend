package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetMaterialUseCase {
    fun material(actor: AcademicActor, id: String): Map<String, Any?>
}
