package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.GetClassDto

interface GetClassInputPort {
    fun classById(request: GetClassDto): AcademicClassView
}
