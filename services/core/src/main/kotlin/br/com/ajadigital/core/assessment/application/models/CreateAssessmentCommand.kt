package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateAssessmentCommand(
    val actor: AuthenticatedActor,
    val key: String,
    val fingerprint: String,
    val draft: AssessmentDraft,
    val questions: AssessmentQuestionSelections,
)
