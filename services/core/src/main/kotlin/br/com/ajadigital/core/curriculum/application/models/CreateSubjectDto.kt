package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateSubjectDto(val actor: AuthenticatedActor, val classId: String, val name: String, val teacherIds: List<String>, val color: String, val icon: String, val description: String, val key: String)
