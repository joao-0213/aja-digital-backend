package br.com.ajadigital.core.assessment.domain.ports.out
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage



interface AttemptStore {
    fun practiceBlocked(actorId: String, role: String, sessionHash: String, questionId: String): Boolean
    fun start(actorId: String, role: String, sessionHash: String, assessmentId: String, key: String, requestHash: String): Map<String, Any?>
    fun attempts(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): AssessmentPage
    fun attempt(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun saveAnswers(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, answers: Map<String, Int?>): Map<String, Any?>?
    fun submit(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>?
    fun result(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
}
