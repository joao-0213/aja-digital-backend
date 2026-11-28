package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.math.BigDecimal

data class SubmissionSaveFeedbackRecord(
    val actor: AuthenticatedActor,
    val id: String,
    val expectedRevision: Int,
    val comment: String,
    val grade: BigDecimal?,
)
