package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.CreateSubmissionCommand
import br.com.ajadigital.core.assessment.domain.models.SubmissionView
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CreateSubmissionInputPort {
    fun create(request: CreateSubmissionCommand): SubmissionView
}
