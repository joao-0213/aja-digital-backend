package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.WithdrawTopicCommand

interface WithdrawTopicInputPort {
    fun withdraw(request: WithdrawTopicCommand): ForumTopicView
}
