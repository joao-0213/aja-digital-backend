package br.com.ajadigital.core.identity.application.usecases
import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import br.com.ajadigital.core.identity.application.models.PasswordResetCommand

import br.com.ajadigital.core.identity.application.ports.`in`.ResetPasswordInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.models.TokenFailure
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import java.security.MessageDigest
import br.com.ajadigital.core.identity.application.models.ResetStudentPasswordDto
import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.models.ResetIdentityPasswordDto


class ResetPasswordUseCase(
    private val store: IdentityStoreOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
) : ResetPasswordInputPort {
    override fun reset(request: ResetStudentPasswordDto): AcceptedResponse {
        val (command) = request
        require(command.token.isNotBlank() && command.token.length <= 256 && command.password.length in 8..128)
        val passwordHash = passwordHasher.hash(HashPasswordDto(command.password))
        when (store.resetPassword(ResetIdentityPasswordDto(sha256(command.token), passwordHash))) {
            null -> return AcceptedResponse()
            TokenFailure.INVALID -> throw AuthTokenInvalid()
            TokenFailure.EXPIRED -> throw AuthTokenExpired()
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
