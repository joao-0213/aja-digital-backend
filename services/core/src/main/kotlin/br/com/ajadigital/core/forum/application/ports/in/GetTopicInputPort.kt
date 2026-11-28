package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.GetTopicQuery

interface GetTopicInputPort {
    fun topic(request: GetTopicQuery): ForumTopicView
}
