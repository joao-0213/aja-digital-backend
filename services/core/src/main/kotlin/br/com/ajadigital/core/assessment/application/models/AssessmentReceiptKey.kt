package br.com.ajadigital.core.assessment.application.models

class AssessmentReceiptKey private constructor(
    val actorId: String,
    val operation: String,
    val target: String,
    val key: String,
    val requestHash: String,
) {
    companion object {
        fun create(actorId: String, key: String, hash: String) =
            AssessmentReceiptKey(actorId, "createAssessment", "/assessments", key, hash)

        fun update(actorId: String, id: String, key: String, hash: String) =
            AssessmentReceiptKey(actorId, "updateAssessment", "/assessments/$id", key, hash)

        fun publish(actorId: String, id: String, key: String, hash: String) =
            AssessmentReceiptKey(actorId, "publishAssessment", "/assessments/$id/publish", key, hash)
    }
}
