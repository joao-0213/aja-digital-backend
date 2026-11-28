package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections

class CreateAssessmentRecord(
    val actor: AuthenticatedActor,
    val key: String,
    val requestHash: String,
    val draft: AssessmentDraft,
    questions: List<AssessmentQuestion>,
) {
    val questions: List<AssessmentQuestion> = Collections.unmodifiableList(questions.toList())
}
