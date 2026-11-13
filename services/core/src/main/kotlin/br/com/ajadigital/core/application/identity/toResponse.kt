package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.ports.out.SessionRecord


fun SessionRecord.toResponse() = SessionResponse(user, expiresAt, id)
