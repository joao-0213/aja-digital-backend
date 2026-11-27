package br.com.ajadigital.core.forum.adapters.out.persistence

import br.com.ajadigital.core.forum.application.models.ForumTopicView
import tools.jackson.databind.ObjectMapper

internal class ForumTopicViewCodec(private val mapper: ObjectMapper) {
    fun decode(value: Map<String, Any?>): ForumTopicView = mapper.convertValue(value, ForumTopicView::class.java)

    @Suppress("UNCHECKED_CAST")
    fun encode(value: ForumTopicView): Map<String, Any?> =
        mapper.readValue(mapper.writeValueAsString(value), Map::class.java) as Map<String, Any?>
}
