package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class PreviewEnrollmentDto(val actor: AuthenticatedActor, val classId: String, val email: String)
