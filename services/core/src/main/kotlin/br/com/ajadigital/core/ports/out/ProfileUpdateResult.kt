package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.domain.identity.User


sealed interface ProfileUpdateResult {
    data class Updated(val user: User) : ProfileUpdateResult
    data class RevisionConflict(val currentRevision: Int) : ProfileUpdateResult
    data object SessionInvalid : ProfileUpdateResult
}
