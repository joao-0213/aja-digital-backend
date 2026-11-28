package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class MarkTopicSolutionCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    val replyId: String?,
)
