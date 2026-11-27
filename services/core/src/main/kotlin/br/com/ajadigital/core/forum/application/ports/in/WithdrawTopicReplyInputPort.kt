package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface WithdrawTopicReplyInputPort {
    fun withdrawReply(request: WithdrawTopicReplyCommand): ForumTopicView
}
