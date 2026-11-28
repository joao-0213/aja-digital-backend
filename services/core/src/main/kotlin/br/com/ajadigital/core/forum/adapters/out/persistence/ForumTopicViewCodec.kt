package br.com.ajadigital.core.forum.adapters.out.persistence

import br.com.ajadigital.core.forum.application.models.ForumTopicView
import tools.jackson.databind.ObjectMapper

internal class ForumTopicViewCodec(private val mapper: ObjectMapper) {
    fun decode(value: Map<String, Any?>): ForumTopicView = mapper.convertValue(value, ForumTopicView::class.java)
    fun decodeJson(value: String): ForumTopicView = mapper.readValue(value, ForumTopicView::class.java)
    fun encode(value: ForumTopicView): String = mapper.writeValueAsString(value)
}
