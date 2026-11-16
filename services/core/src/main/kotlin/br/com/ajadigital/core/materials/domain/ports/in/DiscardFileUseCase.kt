package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface DiscardFileUseCase {
    fun discard(actor: AcademicActor, id: String)
}
