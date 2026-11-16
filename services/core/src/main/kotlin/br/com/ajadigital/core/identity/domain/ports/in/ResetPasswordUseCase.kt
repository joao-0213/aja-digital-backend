package br.com.ajadigital.core.identity.domain.ports.`in`

import br.com.ajadigital.core.identity.domain.models.AcceptedResponse
import br.com.ajadigital.core.identity.domain.models.PasswordResetCommand

interface ResetPasswordUseCase {
    fun reset(command: PasswordResetCommand): AcceptedResponse
}
