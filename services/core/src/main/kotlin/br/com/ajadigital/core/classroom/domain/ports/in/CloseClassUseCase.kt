package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CloseClassUseCase {
    fun closeClass(actor: AcademicActor, id: String, revision: Int, key: String): Map<String, Any?>
}
