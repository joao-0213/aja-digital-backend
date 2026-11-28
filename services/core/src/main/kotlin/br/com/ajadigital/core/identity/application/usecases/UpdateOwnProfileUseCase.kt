package br.com.ajadigital.core.identity.application.usecases
import br.com.ajadigital.core.identity.application.models.ProfileEditCommand
import br.com.ajadigital.core.identity.domain.exceptions.ProfileRevisionConflict
import br.com.ajadigital.core.identity.application.contracts.SessionRequired

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.application.ports.`in`.UpdateOwnProfileInputPort
import br.com.ajadigital.core.identity.application.ports.out.IdentityStoreOutputPort
import br.com.ajadigital.core.identity.application.models.ProfileUpdateResult
import br.com.ajadigital.core.identity.application.models.UpdateOwnProfileDto
import br.com.ajadigital.core.identity.application.models.UpdateIdentityProfileDto
import java.security.MessageDigest


class UpdateOwnProfileUseCase(private val store: IdentityStoreOutputPort) : UpdateOwnProfileInputPort {
    override fun update(request: UpdateOwnProfileDto): User {
        val (cookie, command) = request
                require(command.expectedRevision > 0)
        command.name?.let { require(it.isNotBlank()) }
        if (cookie.isNullOrBlank()) throw SessionRequired()
        return when (val result = store.updateProfile(UpdateIdentityProfileDto(sha256(cookie), command.expectedRevision, command.name?.trim()))) {
            is ProfileUpdateResult.Updated -> result.user
            is ProfileUpdateResult.RevisionConflict -> throw ProfileRevisionConflict(result.currentRevision)
            ProfileUpdateResult.SessionInvalid -> throw SessionRequired()
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
