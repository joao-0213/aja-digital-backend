package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class SubmitAttemptCommand(val actor: AuthenticatedActor, val id: String, val key: String, val body: SubmitAttemptBody?, val requestHash: String)
