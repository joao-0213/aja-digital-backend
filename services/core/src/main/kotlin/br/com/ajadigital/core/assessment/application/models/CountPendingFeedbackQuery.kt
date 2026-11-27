package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CountPendingFeedbackQuery(val actor: AuthenticatedActor)
