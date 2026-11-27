package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ReplyToTopicCommand(val actor: AuthenticatedActor, val id: String, val body: String, val key: String)
