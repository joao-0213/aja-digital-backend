package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class PublishAssessmentCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val key: String,
    val expectedRevision: Int,
    val fingerprint: String,
)
