package br.com.ajadigital.core.curriculum.application.models.store

data class StoreAuthorizeSubjectCommand(val actorId: String, val role: String, val sessionHash: String, val subjectId: String?) {
    override fun toString() = "StoreAuthorizeSubjectCommand(sessionHash=[redacted])"
}
