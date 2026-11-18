package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateClassDto(val actor: AuthenticatedActor, val id: String, val revision: Int, val name: String?, val code: String?, val year: Int?, val shift: String?)
