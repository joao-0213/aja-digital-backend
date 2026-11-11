package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.domain.identity.User
import br.com.ajadigital.core.ports.out.IdentityStore
import br.com.ajadigital.core.ports.out.ProfileUpdateResult
import java.security.MessageDigest

class ProfileRevisionConflict(val currentRevision: Int) : RuntimeException()
data class ProfileEditCommand(val expectedRevision: Int, val name: String?)

class UpdateOwnProfile(private val store: IdentityStore) {
    fun update(cookie: String?, command: ProfileEditCommand): User {
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
