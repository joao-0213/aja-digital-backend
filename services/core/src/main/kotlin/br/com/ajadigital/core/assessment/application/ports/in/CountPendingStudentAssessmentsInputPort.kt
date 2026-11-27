package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CountPendingStudentAssessmentsInputPort {
    fun pendingStudentCount(actor: AuthenticatedActor): Int
}
