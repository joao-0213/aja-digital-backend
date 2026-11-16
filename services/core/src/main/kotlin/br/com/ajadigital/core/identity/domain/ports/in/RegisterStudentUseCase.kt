package br.com.ajadigital.core.identity.domain.ports.`in`

import br.com.ajadigital.core.identity.domain.models.RegistrationCommand
import br.com.ajadigital.core.identity.domain.models.RegistrationResult

interface RegisterStudentUseCase {
    fun register(command: RegistrationCommand): RegistrationResult
}
