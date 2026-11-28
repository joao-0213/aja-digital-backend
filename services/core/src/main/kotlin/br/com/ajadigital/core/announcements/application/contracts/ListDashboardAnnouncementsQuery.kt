package br.com.ajadigital.core.announcements.application.contracts

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListDashboardAnnouncementsQuery(val actor: AuthenticatedActor, val size: Int = 5)
