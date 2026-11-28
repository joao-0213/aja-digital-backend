package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class MarkTopicReadCommand(
    val actor: AuthenticatedActor,
    val id: String,
)
