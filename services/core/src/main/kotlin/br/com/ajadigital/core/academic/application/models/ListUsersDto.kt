package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListUsersDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val role: String?, val active: Boolean?, val q: String?)
