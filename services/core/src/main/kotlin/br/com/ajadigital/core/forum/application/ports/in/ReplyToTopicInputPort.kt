package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.ReplyToTopicCommand

interface ReplyToTopicInputPort {
    fun reply(request: ReplyToTopicCommand): ForumTopicView
}
