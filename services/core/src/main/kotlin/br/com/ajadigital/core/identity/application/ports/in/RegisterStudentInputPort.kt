package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.RegisterStudentDto
import br.com.ajadigital.core.identity.application.models.RegistrationResult

interface RegisterStudentInputPort {
    fun register(request: RegisterStudentDto): RegistrationResult
}
