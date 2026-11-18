package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.CountPendingStudentAssessmentsDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface CountPendingStudentAssessmentsInputPort {
    fun pendingStudentCount(request: CountPendingStudentAssessmentsDto): Int
}
