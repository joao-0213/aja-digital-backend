package br.com.ajadigital.core.assessment.application.models

import java.math.BigDecimal
import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import java.util.Collections

object SubmissionContracts {
    class CreateBody(val assessmentId: String?, attachments: List<AttachmentBody>?, val fieldsValid: Boolean) {
        val attachments: List<AttachmentBody>? = attachments?.let { Collections.unmodifiableList(it.toList()) }
    }
    class UpdateBody(val expectedRevision: Int?, val attachmentsPresent: Boolean, attachments: List<AttachmentBody>?, val fieldsValid: Boolean) {
        val attachments: List<AttachmentBody>? = attachments?.let { Collections.unmodifiableList(it.toList()) }
    }
    data class SubmitBody(val expectedRevision: Int?, val fieldsValid: Boolean)
    data class SaveFeedbackBody(val expectedRevision: Int?, val comment: String?, val grade: BigDecimal?, val fieldsValid: Boolean)
    data class PublishFeedbackBody(val expectedRevision: Int?, val fieldsValid: Boolean)
    data class AttachmentBody(val kind: String?, val name: String?, val fileId: String? = null, val url: String? = null, val fieldsValid: Boolean)
    fun attachments(body: List<AttachmentBody>): List<SubmissionAttachment> {
        require(body.all { it.fieldsValid && it.kind != null && it.name != null })
        return body.map { SubmissionAttachment(requireNotNull(it.kind), requireNotNull(it.name), it.fileId, it.url) }
    }
}
