package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class StoreMaterialsQuery(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: MaterialSort, val moduleId: String?, val subjectId: String?)
