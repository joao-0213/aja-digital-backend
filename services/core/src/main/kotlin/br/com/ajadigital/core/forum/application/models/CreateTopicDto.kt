package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateTopicDto(val actor: AuthenticatedActor, val draft: TopicDraft, val key: String)
