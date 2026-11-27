package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface VoteTopicReplyInputPort {
    fun vote(request: VoteTopicReplyCommand): ForumTopicView
}
