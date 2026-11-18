package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class SaveAttemptAnswersDto(val actor: AuthenticatedActor, val id: String, val body: Any?)
