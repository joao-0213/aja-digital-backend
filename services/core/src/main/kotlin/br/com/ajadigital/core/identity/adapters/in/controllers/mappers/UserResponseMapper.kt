package br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.UserResponse
import br.com.ajadigital.core.identity.domain.models.User

internal fun User.toResponse() = UserResponse(id, revision, createdAt, updatedAt, updatedBy, name, email, role, active, emailConfirmedAt)
