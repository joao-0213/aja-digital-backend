package br.com.ajadigital.core.curriculum.application.contracts

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListSubjectsQuery(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val classId: String?, val query: String?)
