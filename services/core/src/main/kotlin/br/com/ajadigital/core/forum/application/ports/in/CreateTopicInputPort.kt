package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.CreateTopicCommand
import br.com.ajadigital.core.forum.application.models.ForumTopicView

interface CreateTopicInputPort {
    fun create(request: CreateTopicCommand): ForumTopicView
}
