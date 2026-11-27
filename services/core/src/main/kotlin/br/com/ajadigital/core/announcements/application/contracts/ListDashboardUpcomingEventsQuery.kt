package br.com.ajadigital.core.announcements.application.contracts

import br.com.ajadigital.core.announcements.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.time.LocalDate

data class ListDashboardUpcomingEventsQuery(val actor: AuthenticatedActor, val from: LocalDate, val to: LocalDate, val size: Int = 5)
