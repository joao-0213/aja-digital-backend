package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange

data class UpdateModuleCommand(val actor: AuthenticatedActor, val id: String, val revision: Int, val title: FieldChange<String>, val description: FieldChange<String>)
