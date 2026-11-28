package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.CreateEnrollmentDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CreateEnrollmentInputPort {
    fun createEnrollment(request: CreateEnrollmentDto): AcademicEnrollmentView
}
