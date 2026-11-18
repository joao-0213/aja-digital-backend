package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.WithdrawTopicDto

interface WithdrawTopicInputPort {
    fun withdraw(request: WithdrawTopicDto): Map<String, Any?>
}
