package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateModuleCommand(val actor: AuthenticatedActor, val subjectId: String, val title: String, val description: String, val key: String)
