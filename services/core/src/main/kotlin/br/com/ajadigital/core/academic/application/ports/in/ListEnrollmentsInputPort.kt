package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.ListEnrollmentsDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.Page

interface ListEnrollmentsInputPort {
    fun enrollments(request: ListEnrollmentsDto): Page<AcademicEnrollmentView>
}
