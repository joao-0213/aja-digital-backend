package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.ListAssessmentsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface ListAssessmentsInputPort {
    fun assessments(request: ListAssessmentsDto): Map<String, Any?>
}
