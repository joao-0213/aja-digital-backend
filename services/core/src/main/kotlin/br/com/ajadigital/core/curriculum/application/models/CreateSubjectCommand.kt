package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class CreateSubjectCommand(
    val actor: AuthenticatedActor,
    val classId: String,
    val name: String,
    teacherIds: List<String>,
    val color: String,
    val icon: String,
    val description: String,
    val key: String,
) {
    val teacherIds: List<String> = teacherIds.toList()
}
