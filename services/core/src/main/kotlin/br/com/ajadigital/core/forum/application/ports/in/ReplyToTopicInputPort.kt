package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface ReplyToTopicInputPort {
    fun reply(request: ReplyToTopicCommand): ForumTopicView
}
