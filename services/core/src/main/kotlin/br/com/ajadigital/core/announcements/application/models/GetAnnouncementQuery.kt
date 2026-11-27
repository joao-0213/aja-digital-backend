package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetAnnouncementQuery(val actor: AuthenticatedActor, val id: String)
