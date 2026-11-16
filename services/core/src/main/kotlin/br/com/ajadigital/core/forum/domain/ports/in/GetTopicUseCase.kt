package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface GetTopicUseCase {
    fun topic(actor: AcademicActor, id: String): Map<String, Any?>
}
