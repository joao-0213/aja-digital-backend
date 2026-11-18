package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.WithdrawTopicReplyDto

interface WithdrawTopicReplyInputPort {
    fun withdrawReply(request: WithdrawTopicReplyDto): Map<String, Any?>
}
