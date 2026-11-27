package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.ListMyEnrollmentsDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

import br.com.ajadigital.core.shared.application.contracts.Page

interface ListMyEnrollmentsInputPort {
    fun myEnrollments(request: ListMyEnrollmentsDto): Page<AcademicEnrollmentView>
}
