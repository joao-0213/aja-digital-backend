package br.com.ajadigital.core.identity.application.models

import br.com.ajadigital.core.identity.domain.models.SessionRecord


data class AuthenticatedSession(val session: SessionRecord, val cookieValue: String)
