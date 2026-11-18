package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.AuthenticatedSession
import br.com.ajadigital.core.identity.application.models.LoginStudentDto

interface LoginInputPort {
    fun login(request: LoginStudentDto): AuthenticatedSession
}
