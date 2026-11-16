package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ReplyToTopicUseCase {
    fun reply(actor: AcademicActor, id: String, body: String, key: String): Map<String, Any?>
}
