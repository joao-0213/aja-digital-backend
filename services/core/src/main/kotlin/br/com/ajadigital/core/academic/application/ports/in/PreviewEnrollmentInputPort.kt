package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.PreviewEnrollmentDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface PreviewEnrollmentInputPort {
    fun preview(request: PreviewEnrollmentDto): Map<String, Any?>
}
