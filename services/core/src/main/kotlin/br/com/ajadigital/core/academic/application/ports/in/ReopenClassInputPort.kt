package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.ReopenClassDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface ReopenClassInputPort {
    fun reopenClass(request: ReopenClassDto): AcademicClassView
}
