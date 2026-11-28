package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class CreateSubmissionCommand(val actor: AuthenticatedActor, val key: String, val body: SubmissionContracts.CreateBody, val requestHash: String)
