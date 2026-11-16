package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface MarkTopicReadUseCase {
    fun read(actor: AcademicActor, id: String): Map<String, Any?>
}
