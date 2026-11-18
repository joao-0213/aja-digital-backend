package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.ReplyToTopicDto

interface ReplyToTopicInputPort {
    fun reply(request: ReplyToTopicDto): Map<String, Any?>
}
