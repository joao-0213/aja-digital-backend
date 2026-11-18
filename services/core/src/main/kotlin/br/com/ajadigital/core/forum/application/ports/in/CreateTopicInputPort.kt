package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.CreateTopicDto
import br.com.ajadigital.core.forum.domain.models.TopicDraft

interface CreateTopicInputPort {
    fun create(request: CreateTopicDto): Map<String, Any?>
}
