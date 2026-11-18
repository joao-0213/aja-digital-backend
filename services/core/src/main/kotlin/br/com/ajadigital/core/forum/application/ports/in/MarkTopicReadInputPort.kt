package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.MarkTopicReadDto

interface MarkTopicReadInputPort {
    fun read(request: MarkTopicReadDto): Map<String, Any?>
}
