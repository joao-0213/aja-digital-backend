package br.com.ajadigital.core.materials.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class DiscardFileCommand(val actor: AuthenticatedActor, val id: String)
