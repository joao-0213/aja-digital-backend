package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListSubmissionsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListSubmissionsInputPort {
    fun submissions(request: ListSubmissionsDto): Map<String, Any?>
}
