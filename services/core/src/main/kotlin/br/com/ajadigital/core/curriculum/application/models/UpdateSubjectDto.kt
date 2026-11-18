package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class UpdateSubjectDto(val actor: AuthenticatedActor, val id: String, val revision: Int, val classId: String?, val name: String?, val teacherIds: List<String>?, val color: String?, val icon: String?, val description: String?)
