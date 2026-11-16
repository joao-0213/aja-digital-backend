package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateClassUseCase {
    fun createClass(actor: AcademicActor, name: String, code: String, year: Int, shift: String, key: String): Map<String, Any?>
}
