package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.ListClassesDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

import br.com.ajadigital.core.shared.application.contracts.Page

interface ListClassesInputPort {
    fun classes(request: ListClassesDto): Page<AcademicClassView>
}
