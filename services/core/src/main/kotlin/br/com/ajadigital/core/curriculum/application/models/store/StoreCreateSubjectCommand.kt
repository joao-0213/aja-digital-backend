package br.com.ajadigital.core.curriculum.application.models.store

data class StoreCreateSubjectCommand(val actorId: String, val sessionHash: String, val classId: String, val name: String, val teacherIds: List<String>, val color: String, val icon: String, val description: String) {
    override fun toString() = "StoreCreateSubjectCommand(sessionHash=[redacted])"
}
