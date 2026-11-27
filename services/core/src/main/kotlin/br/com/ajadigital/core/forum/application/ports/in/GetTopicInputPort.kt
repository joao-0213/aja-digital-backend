package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface GetTopicInputPort {
    fun topic(request: GetTopicQuery): ForumTopicView
}
