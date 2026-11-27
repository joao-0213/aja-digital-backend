package br.com.ajadigital.core.assessment.application.contracts

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CountPendingFeedbackQuery(val actor: AuthenticatedActor)
