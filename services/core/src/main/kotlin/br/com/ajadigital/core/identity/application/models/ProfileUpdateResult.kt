package br.com.ajadigital.core.identity.application.models

import br.com.ajadigital.core.identity.domain.models.User

sealed interface ProfileUpdateResult {
    data class Updated(
        val user: User,
    ) : ProfileUpdateResult

    data class RevisionConflict(
        val currentRevision: Int,
    ) : ProfileUpdateResult

    data object SessionInvalid : ProfileUpdateResult
}
