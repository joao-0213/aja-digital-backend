package br.com.ajadigital.core.assessment.domain.ports.out
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion



interface AssessmentStore {
    fun authorizeWrite(actorId: String, role: String, sessionHash: String, moduleId: String)
    fun create(actorId: String, role: String, sessionHash: String, key: String, requestHash: String, draft: AssessmentDraft, questions: List<AssessmentQuestion>): Map<String, Any?>
    fun update(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int, draft: AssessmentDraft?, questions: List<AssessmentQuestion>?, confirmResetDrafts: Boolean): Map<String, Any?>?
    fun publish(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>?
    fun assessment(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun assessments(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): AssessmentPage
    fun dashboardAssessments(actorId: String, role: String, sessionHash: String, pageSize: Int): AssessmentPage
    fun pendingStudentCount(actorId: String, sessionHash: String): Int
}
