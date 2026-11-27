package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class SubmissionSubmitRecord(
    val actor: AuthenticatedActor, val id: String, val expectedRevision: Int, val key: String, val requestHash: String,
)
