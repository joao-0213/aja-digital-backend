package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.models.GetUserDto

interface GetUserInputPort {
    fun user(request: GetUserDto): AcademicUserView
}
