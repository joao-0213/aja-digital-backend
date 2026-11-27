package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface CreateTopicInputPort {
    fun create(request: CreateTopicCommand): ForumTopicView
}
