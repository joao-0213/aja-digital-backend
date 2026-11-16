package br.com.ajadigital.core.identity.domain.models

import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord


data class AuthenticatedSession(val session: SessionRecord, val cookieValue: String)
