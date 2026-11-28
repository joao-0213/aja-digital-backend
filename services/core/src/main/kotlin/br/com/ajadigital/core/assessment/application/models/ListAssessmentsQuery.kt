package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListAssessmentsQuery(val actor: AuthenticatedActor, val page: Int, val pageSize: Int, val sort: String, val subjectId: String?, val moduleId: String?, val kind: String?, val status: String?)
