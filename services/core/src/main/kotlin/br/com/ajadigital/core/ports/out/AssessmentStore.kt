package br.com.ajadigital.core.ports.out

import java.time.Instant

data class AssessmentDraft(
    val moduleId: String,
    val title: String,
    val description: String,
    val kind: String,
    val maxGrade: Double?,
    val deadline: Instant?,
    val required: Boolean,
    val allowLate: Boolean,
)

data class AssessmentQuestion(
    val id: String,
    val source: String,
    val snapshot: Map<String, Any?>?,
    val sourceRevision: Int? = null,
    val contentHash: String? = null,
)

data class AssessmentPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Long)

interface AssessmentStore {
    fun authorizeWrite(actorId: String, role: String, sessionHash: String, moduleId: String)
    fun create(actorId: String, role: String, sessionHash: String, key: String, requestHash: String, draft: AssessmentDraft, questions: List<AssessmentQuestion>): Map<String, Any?>
    fun update(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int, draft: AssessmentDraft?, questions: List<AssessmentQuestion>?, confirmResetDrafts: Boolean): Map<String, Any?>?
    fun publish(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>?
    fun assessment(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun assessments(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): AssessmentPage
}

interface AttemptStore {
    fun start(actorId: String, role: String, sessionHash: String, assessmentId: String, key: String, requestHash: String): Map<String, Any?>
    fun attempts(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): AssessmentPage
    fun attempt(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun saveAnswers(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, answers: Map<String, Int?>): Map<String, Any?>?
    fun submit(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>?
    fun result(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
}

interface QuestionSnapshotProvider {
    fun snapshots(ids: List<String>): List<BankQuestionSnapshot>
}

data class BankQuestionSnapshot(
    val questionId: String,
    val revision: Int,
    val contentHash: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val image: BankQuestionImage?,
)

data class BankQuestionImage(val fileId: String, val alt: String)
