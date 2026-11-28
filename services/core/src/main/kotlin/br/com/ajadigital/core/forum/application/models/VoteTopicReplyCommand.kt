package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class VoteTopicReplyCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val replyId: String,
    val voted: Boolean,
)
