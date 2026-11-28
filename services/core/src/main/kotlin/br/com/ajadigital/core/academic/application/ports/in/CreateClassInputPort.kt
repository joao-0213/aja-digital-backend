package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.CreateClassDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface CreateClassInputPort {
    fun createClass(request: CreateClassDto): AcademicClassView
}
