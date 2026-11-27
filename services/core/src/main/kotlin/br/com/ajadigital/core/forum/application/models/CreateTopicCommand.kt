package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateTopicCommand(val actor: AuthenticatedActor, val draft: TopicDraft, val key: String)
