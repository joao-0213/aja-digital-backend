package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.academic.application.models.AcademicPage

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListSubjectsDto(val actor: AuthenticatedActor, val page: Int, val size: Int, val sort: String, val classId: String?, val query: String?)
