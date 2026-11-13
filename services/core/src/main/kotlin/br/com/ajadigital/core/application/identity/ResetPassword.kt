package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.ports.out.IdentityStore
import br.com.ajadigital.core.ports.out.TokenFailure
import br.com.ajadigital.core.ports.out.PasswordHasher
import java.security.MessageDigest


class ResetPassword(
    private val store: IdentityStore,
    private val passwordHasher: PasswordHasher,
) {
    fun reset(command: PasswordResetCommand): AcceptedResponse {
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
