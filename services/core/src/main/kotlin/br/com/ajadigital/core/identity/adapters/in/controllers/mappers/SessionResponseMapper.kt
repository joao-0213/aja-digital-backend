package br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.SessionResponse
import br.com.ajadigital.core.identity.domain.models.SessionRecord

internal object SessionResponseMapper {
    fun map(session: SessionRecord) = SessionResponse(session.user.toResponse(), session.expiresAt, session.id)
}
