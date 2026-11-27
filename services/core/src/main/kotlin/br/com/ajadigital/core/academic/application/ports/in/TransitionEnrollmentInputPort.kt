package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.TransitionEnrollmentDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface TransitionEnrollmentInputPort {
    fun transitionEnrollment(request: TransitionEnrollmentDto): AcademicEnrollmentView
}
