package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.VoteTopicReplyCommand

interface VoteTopicReplyInputPort {
    fun vote(request: VoteTopicReplyCommand): ForumTopicView
}
