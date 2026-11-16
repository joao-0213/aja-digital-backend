package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ReopenClassUseCase {
    fun reopenClass(actor: AcademicActor, id: String, revision: Int, key: String): Map<String, Any?>
}
