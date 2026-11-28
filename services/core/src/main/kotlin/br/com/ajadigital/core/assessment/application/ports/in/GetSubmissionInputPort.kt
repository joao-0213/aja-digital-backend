package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.GetSubmissionQuery
import br.com.ajadigital.core.assessment.domain.models.SubmissionView
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface GetSubmissionInputPort {
    fun submission(request: GetSubmissionQuery): SubmissionView
}
