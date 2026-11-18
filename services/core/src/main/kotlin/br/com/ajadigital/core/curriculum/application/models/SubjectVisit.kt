package br.com.ajadigital.core.curriculum.application.models

import java.util.Collections

class SubjectVisit(
    val subjectId: String,
    val seenAt: String?,
    seenPublicationIds: List<String>,
    publicationIds: List<String>,
    val hasUpdates: Boolean,
) {
    val seenPublicationIds: List<String> = Collections.unmodifiableList(seenPublicationIds.toList())
    val publicationIds: List<String> = Collections.unmodifiableList(publicationIds.toList())
}
