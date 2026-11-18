package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.GetEnrollmentDto

interface GetEnrollmentInputPort {
    fun enrollment(request: GetEnrollmentDto): Map<String, Any?>
}
