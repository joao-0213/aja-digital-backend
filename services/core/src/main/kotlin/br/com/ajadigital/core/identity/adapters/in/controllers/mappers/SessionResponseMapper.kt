package br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers

import br.com.ajadigital.core.identity.domain.models.SessionRecord
import br.com.ajadigital.core.identity.application.models.SessionResponse

internal object SessionResponseMapper {
    fun map(session: SessionRecord) = SessionResponse(session.user, session.expiresAt, session.id)
}
