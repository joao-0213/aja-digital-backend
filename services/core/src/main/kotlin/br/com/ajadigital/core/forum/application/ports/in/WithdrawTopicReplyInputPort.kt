package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.WithdrawTopicReplyCommand

interface WithdrawTopicReplyInputPort {
    fun withdrawReply(request: WithdrawTopicReplyCommand): ForumTopicView
}
