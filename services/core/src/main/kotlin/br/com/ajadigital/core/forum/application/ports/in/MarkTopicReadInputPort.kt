package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface MarkTopicReadInputPort {
    fun read(request: MarkTopicReadCommand): ForumTopicView
}
