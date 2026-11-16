package br.com.ajadigital.core.identity.domain.ports.`in`

import br.com.ajadigital.core.identity.domain.models.ProfileEditCommand
import br.com.ajadigital.core.identity.domain.models.User

interface UpdateOwnProfileUseCase {
    fun update(cookie: String?, command: ProfileEditCommand): User
}
