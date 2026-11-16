package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.models.TopicDraft

interface CreateTopicUseCase {
    fun create(actor: AcademicActor, draft: TopicDraft, key: String): Map<String, Any?>
}
