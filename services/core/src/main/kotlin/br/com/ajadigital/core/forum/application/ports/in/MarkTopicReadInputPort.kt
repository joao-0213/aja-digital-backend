package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.MarkTopicReadCommand

interface MarkTopicReadInputPort {
    fun read(request: MarkTopicReadCommand): ForumTopicView
}
