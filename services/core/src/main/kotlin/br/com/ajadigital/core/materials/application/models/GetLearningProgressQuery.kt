package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class GetLearningProgressQuery(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: ProgressSort?, val subjectId: String?, val materialId: String?)
