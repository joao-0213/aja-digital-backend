package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.UpdateOwnProfileDto
import br.com.ajadigital.core.identity.domain.models.User

interface UpdateOwnProfileInputPort {
    fun update(request: UpdateOwnProfileDto): User
}
