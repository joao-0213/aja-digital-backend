package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections

class SubmissionUpdateRecord(
    val actor: AuthenticatedActor,
    val id: String,
    val expectedRevision: Int,
    attachments: List<SubmissionAttachment>?,
) {
    val attachments: List<SubmissionAttachment>? = attachments?.let { Collections.unmodifiableList(it.toList()) }
}
