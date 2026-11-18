package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.LogoutStudentSessionDto

interface LogoutInputPort {
    fun logout(request: LogoutStudentSessionDto)
}
