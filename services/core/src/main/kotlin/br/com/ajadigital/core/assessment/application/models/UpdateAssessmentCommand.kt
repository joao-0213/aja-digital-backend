package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange

data class UpdateAssessmentCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val key: String,
    val fingerprint: String,
    val expectedRevision: Int,
    val changes: AssessmentChanges?,
    val questions: FieldChange<AssessmentQuestionSelections?>,
    val confirmResetDrafts: Boolean?,
)
