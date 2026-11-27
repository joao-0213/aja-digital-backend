package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListSubmissionsQuery
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListSubmissionsInputPort {
    fun submissions(request: ListSubmissionsQuery): SubmissionPage
}
