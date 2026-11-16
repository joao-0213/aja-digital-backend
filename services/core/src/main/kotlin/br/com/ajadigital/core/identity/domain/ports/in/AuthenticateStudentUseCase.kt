package br.com.ajadigital.core.identity.domain.ports.`in`

import br.com.ajadigital.core.identity.domain.models.AuthenticatedSession
import br.com.ajadigital.core.identity.domain.models.LoginCommand
import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord

interface AuthenticateStudentUseCase {
    fun login(command: LoginCommand): AuthenticatedSession
    fun resolve(cookieValue: String?): SessionRecord
    fun logout(cookieValue: String?)
}
