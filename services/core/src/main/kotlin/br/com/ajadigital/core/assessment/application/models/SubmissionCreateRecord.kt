package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections

class SubmissionCreateRecord(
    val actor: AuthenticatedActor, val assessmentId: String, attachments: List<SubmissionAttachment>,
    val key: String, val requestHash: String,
) {
    val attachments: List<SubmissionAttachment> = Collections.unmodifiableList(attachments.toList())
}
