package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class SubmitSubmissionCommand(val actor: AuthenticatedActor, val id: String, val key: String, val body: SubmissionContracts.SubmitBody, val requestHash: String)
