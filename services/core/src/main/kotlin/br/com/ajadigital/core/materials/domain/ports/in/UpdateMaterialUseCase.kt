package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateMaterialUseCase {
    fun update(actor: AcademicActor, id: String, revision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?>
}
