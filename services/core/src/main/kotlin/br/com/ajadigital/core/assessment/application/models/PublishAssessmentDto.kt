package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class PublishAssessmentDto(val actor: AuthenticatedActor, val id: String, val key: String, val expectedRevision: Int, val body: Any?)
