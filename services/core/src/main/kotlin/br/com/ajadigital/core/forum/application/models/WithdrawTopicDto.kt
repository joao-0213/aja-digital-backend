package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class WithdrawTopicDto(val actor: AuthenticatedActor, val id: String, val revision: Int)
