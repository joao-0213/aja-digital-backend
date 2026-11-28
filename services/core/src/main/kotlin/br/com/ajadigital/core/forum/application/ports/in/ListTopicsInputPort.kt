package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicPage
import br.com.ajadigital.core.forum.application.models.ListTopicsQuery

interface ListTopicsInputPort {
    fun topics(request: ListTopicsQuery): ForumTopicPage
}
