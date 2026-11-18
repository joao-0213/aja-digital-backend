package br.com.ajadigital.core.academic.application.models
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateEnrollmentDto(val actor: AuthenticatedActor, val classId: String, val emailInput: String, val key: String)
