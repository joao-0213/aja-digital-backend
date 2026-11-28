package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.GetEnrollmentDto

interface GetEnrollmentInputPort {
    fun enrollment(request: GetEnrollmentDto): AcademicEnrollmentView
}
