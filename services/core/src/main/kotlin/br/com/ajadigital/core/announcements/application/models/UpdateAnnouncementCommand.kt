package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateAnnouncementCommand(val actor: AuthenticatedActor, val id: String, val revision: Int, val draft: AnnouncementDraft)
