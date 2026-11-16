package br.com.ajadigital.core.identity.domain.usecases
import br.com.ajadigital.core.identity.domain.models.SessionResponse

import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord


fun SessionRecord.toResponse() = SessionResponse(user, expiresAt, id)
