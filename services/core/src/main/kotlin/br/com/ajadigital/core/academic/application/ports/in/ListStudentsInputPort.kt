package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicStudentSummaryView
import br.com.ajadigital.core.academic.application.models.ListStudentsDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.Page

interface ListStudentsInputPort {
    fun students(request: ListStudentsDto): Page<AcademicStudentSummaryView>
}
