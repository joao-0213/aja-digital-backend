package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class VisitSubjectDto(val actor: AuthenticatedActor, val subjectId: String, val publicationIds: List<String>? = null)
