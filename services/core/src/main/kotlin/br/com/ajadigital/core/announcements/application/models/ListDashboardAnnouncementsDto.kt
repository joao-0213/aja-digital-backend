package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListDashboardAnnouncementsDto(val actor: AuthenticatedActor, val size: Int = 5)
