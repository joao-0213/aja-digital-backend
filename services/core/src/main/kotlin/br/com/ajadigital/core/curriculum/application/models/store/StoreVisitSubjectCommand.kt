package br.com.ajadigital.core.curriculum.application.models.store

data class StoreVisitSubjectCommand(
    val subjectId: String,
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val publicationIds: List<String>?,
) {
    override fun toString() = "StoreVisitSubjectCommand(sessionHash=[redacted])"
}
