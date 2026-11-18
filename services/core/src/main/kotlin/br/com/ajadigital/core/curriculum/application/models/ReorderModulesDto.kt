package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ReorderModulesDto(val actor: AuthenticatedActor, val subjectId: String, val revision: Int, val items: List<Pair<String, Int>>, val page: Int, val pageSize: Int)
