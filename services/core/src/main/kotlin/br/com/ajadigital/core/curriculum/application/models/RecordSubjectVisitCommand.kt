package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class RecordSubjectVisitCommand(
    val actor: AuthenticatedActor,
    val subjectId: String,
    publicationIds: List<String>,
) {
    val publicationIds: List<String> = publicationIds.toList()
}
