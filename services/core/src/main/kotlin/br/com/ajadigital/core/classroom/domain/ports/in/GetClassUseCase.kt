package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetClassUseCase {
    fun classById(actor: AcademicActor, id: String): Map<String, Any?>
}
