package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListEnrollmentsDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val classId: String?, val studentId: String?, val status: List<String>?, val q: String?)
