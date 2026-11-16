package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.models.ProfileEditCommand
import br.com.ajadigital.core.identity.domain.exceptions.ProfileRevisionConflict
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired

import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.ports.`in`.UpdateOwnProfileUseCase
import br.com.ajadigital.core.identity.domain.ports.out.IdentityStore
import br.com.ajadigital.core.identity.domain.ports.out.ProfileUpdateResult
import java.security.MessageDigest


class UpdateOwnProfile(private val store: IdentityStore) : UpdateOwnProfileUseCase {
    override fun update(cookie: String?, command: ProfileEditCommand): User {
        require(command.expectedRevision > 0)
        command.name?.let { require(it.isNotBlank()) }
        if (cookie.isNullOrBlank()) throw SessionRequired()
        return when (val result = store.updateProfile(sha256(cookie), command.expectedRevision, command.name?.trim())) {
            is ProfileUpdateResult.Updated -> result.user
            is ProfileUpdateResult.RevisionConflict -> throw ProfileRevisionConflict(result.currentRevision)
            ProfileUpdateResult.SessionInvalid -> throw SessionRequired()
        }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
