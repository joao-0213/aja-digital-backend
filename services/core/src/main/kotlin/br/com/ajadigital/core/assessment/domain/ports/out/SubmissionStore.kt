package br.com.ajadigital.core.assessment.domain.ports.out
import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage



interface SubmissionStore {
    fun create(actorId: String, role: String, sessionHash: String, assessmentId: String, attachments: List<SubmissionAttachment>, key: String, requestHash: String): Map<String, Any?>
    fun submissions(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): SubmissionPage
    fun pendingFeedbackCount(actorId: String, role: String, sessionHash: String): Int
    fun submission(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun update(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, attachments: List<SubmissionAttachment>?): Map<String, Any?>?
    fun submit(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, key: String, requestHash: String): Map<String, Any?>?
    fun saveFeedback(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, comment: String, grade: java.math.BigDecimal?): Map<String, Any?>?
    fun publishFeedback(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, key: String, requestHash: String): Map<String, Any?>?
}
