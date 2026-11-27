package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface WithdrawTopicInputPort {
    fun withdraw(request: WithdrawTopicCommand): ForumTopicView
}
