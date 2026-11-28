package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class WithdrawTopicReplyCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val replyId: String,
    val revision: Int,
)
