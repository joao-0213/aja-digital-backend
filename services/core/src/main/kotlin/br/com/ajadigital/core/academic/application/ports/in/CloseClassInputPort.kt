package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.CloseClassDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CloseClassInputPort {
    fun closeClass(request: CloseClassDto): AcademicClassView
}
