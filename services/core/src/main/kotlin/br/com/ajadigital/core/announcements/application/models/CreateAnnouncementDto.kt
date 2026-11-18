package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateAnnouncementDto(val actor: AuthenticatedActor, val draft: AnnouncementDraft, val key: String)
