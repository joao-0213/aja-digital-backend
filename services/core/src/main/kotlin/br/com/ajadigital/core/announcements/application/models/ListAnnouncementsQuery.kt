package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.announcements.domain.models.*

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListAnnouncementsQuery(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val classId: String?, val subjectId: String?, val query: String?)
