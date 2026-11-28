package br.com.ajadigital.core.announcements.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class WithdrawAnnouncementCommand(val actor: AuthenticatedActor, val id: String, val revision: Int)
