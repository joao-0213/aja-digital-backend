package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListCalendarEventsQuery(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val from: String?, val to: String?, val subjectId: String?)
