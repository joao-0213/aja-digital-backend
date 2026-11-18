package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.VoteTopicReplyDto

interface VoteTopicReplyInputPort {
    fun vote(request: VoteTopicReplyDto): Map<String, Any?>
}
