package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.GetTopicDto

interface GetTopicInputPort {
    fun topic(request: GetTopicDto): Map<String, Any?>
}
