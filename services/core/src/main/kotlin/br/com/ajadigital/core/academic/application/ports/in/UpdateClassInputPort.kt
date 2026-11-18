package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.UpdateClassDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface UpdateClassInputPort {
    fun updateClass(request: UpdateClassDto): Map<String, Any?>
}
