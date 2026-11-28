package br.com.ajadigital.core.identity.application.usecases
import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.identity.application.models.HashPasswordDto
import br.com.ajadigital.core.identity.application.models.PasswordResetCommand
import br.com.ajadigital.core.identity.application.models.ResetIdentityPasswordDto
import br.com.ajadigital.core.identity.application.models.ResetStudentPasswordDto
import br.com.ajadigital.core.identity.application.models.TokenFailure
import br.com.ajadigital.core.identity.application.ports.`in`.ResetPasswordInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import java.security.MessageDigest

class ResetPasswordUseCase(
    private val store: IdentityStoreOutputPort,
    private val passwordHasher: PasswordHasherOutputPort,
) : ResetPasswordInputPort {
    override fun reset(request: ResetStudentPasswordDto): AcceptedResponse {
        val (command) = request
        require(command.token.isNotBlank() && command.token.length <= MAX_AUTH_TOKEN_LENGTH && command.password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH)
        val passwordHash = passwordHasher.hash(HashPasswordDto(command.password))
        when (store.resetPassword(ResetIdentityPasswordDto(sha256(command.token), passwordHash))) {
            null -> return AcceptedResponse()
            TokenFailure.INVALID -> throw AuthTokenInvalid()
            TokenFailure.EXPIRED -> throw AuthTokenExpired()
        }
    }

    private fun sha256(value: String) = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 128
private const val MAX_AUTH_TOKEN_LENGTH = 256
