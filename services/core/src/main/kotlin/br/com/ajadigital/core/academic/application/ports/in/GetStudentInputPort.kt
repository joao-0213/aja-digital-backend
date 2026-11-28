package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicStudentView
import br.com.ajadigital.core.academic.application.models.GetStudentDto

interface GetStudentInputPort {
    fun student(request: GetStudentDto): AcademicStudentView
}
