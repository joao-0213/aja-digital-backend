package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListStudentsDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val classId: String?, val q: String?, val history: Boolean)
