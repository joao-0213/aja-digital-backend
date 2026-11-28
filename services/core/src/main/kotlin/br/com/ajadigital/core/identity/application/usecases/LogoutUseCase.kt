package br.com.ajadigital.core.identity.application.usecases

import br.com.ajadigital.core.identity.application.models.LogoutStudentSessionDto
import br.com.ajadigital.core.identity.application.models.RevokeIdentitySessionDto
import br.com.ajadigital.core.identity.application.ports.`in`.LogoutInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import java.security.MessageDigest

class LogoutUseCase(
    private val store: IdentityStoreOutputPort,
) : LogoutInputPort {
    override fun logout(request: LogoutStudentSessionDto) {
        store.revokeSession(RevokeIdentitySessionDto(request.cookieValue?.takeIf(String::isNotBlank)?.let(::sha256)))
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
