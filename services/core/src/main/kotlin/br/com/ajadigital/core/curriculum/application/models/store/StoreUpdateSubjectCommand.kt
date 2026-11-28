package br.com.ajadigital.core.curriculum.application.models.store

data class StoreUpdateSubjectCommand(
    val id: String,
    val actorId: String,
    val sessionHash: String,
    val expectedRevision: Int,
    val classId: String?,
    val name: String?,
    val teacherIds: List<String>?,
    val color: String?,
    val icon: String?,
    val description: String?,
) {
    override fun toString() = "StoreUpdateSubjectCommand(sessionHash=[redacted])"
}
