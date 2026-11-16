package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.models.AcceptedResponse
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import br.com.ajadigital.core.identity.domain.models.PasswordResetCommand

import br.com.ajadigital.core.identity.domain.ports.`in`.ResetPasswordUseCase
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import br.com.ajadigital.core.identity.domain.ports.out.TokenFailure
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import java.security.MessageDigest


class ResetPassword(
    private val store: IdentityStore,
    private val passwordHasher: PasswordHasher,
) : ResetPasswordUseCase {
    override fun reset(command: PasswordResetCommand): AcceptedResponse {
        require(command.token.isNotBlank() && command.token.length <= 256 && command.password.length in 8..128)
        val passwordHash = passwordHasher.hash(command.password)
        when (store.resetPassword(sha256(command.token), passwordHash)) {
            null -> return AcceptedResponse()
            TokenFailure.INVALID -> throw AuthTokenInvalid()
            TokenFailure.EXPIRED -> throw AuthTokenExpired()
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
