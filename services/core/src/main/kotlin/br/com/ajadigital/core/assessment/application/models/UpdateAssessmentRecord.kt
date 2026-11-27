package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion

class UpdateAssessmentRecord(val actor: AuthenticatedActor, val id: String, val key: String, val requestHash: String, val expectedRevision: Int, val draft: AssessmentDraft, questions: List<AssessmentQuestion>?, val confirmResetDrafts: Boolean) {
    val questions: List<AssessmentQuestion>? = questions?.let { Collections.unmodifiableList(it.toList()) }
}
