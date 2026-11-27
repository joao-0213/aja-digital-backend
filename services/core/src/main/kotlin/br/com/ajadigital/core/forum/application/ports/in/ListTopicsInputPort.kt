package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

interface ListTopicsInputPort {
    fun topics(request: ListTopicsQuery): ForumTopicPage
}
