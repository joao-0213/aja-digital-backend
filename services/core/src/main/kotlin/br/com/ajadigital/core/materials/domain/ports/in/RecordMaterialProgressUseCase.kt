package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface RecordMaterialProgressUseCase {
    fun recordProgress(actor: AcademicActor, materialId: String, completed: Boolean?): Map<String, Any?>
}
