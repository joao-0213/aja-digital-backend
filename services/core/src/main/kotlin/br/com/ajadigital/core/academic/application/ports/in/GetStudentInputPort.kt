package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.GetStudentDto

interface GetStudentInputPort {
    fun student(request: GetStudentDto): Map<String, Any?>
}
