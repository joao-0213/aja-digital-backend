package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.identity.application.models.ResetStudentPasswordDto

interface ResetPasswordInputPort {
    fun reset(request: ResetStudentPasswordDto): AcceptedResponse
}
