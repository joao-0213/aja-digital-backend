package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.GetClassDto

interface GetClassInputPort {
    fun classById(request: GetClassDto): Map<String, Any?>
}
