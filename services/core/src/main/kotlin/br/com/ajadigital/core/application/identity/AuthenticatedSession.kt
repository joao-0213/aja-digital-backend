package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.ports.out.SessionRecord


data class AuthenticatedSession(val session: SessionRecord, val cookieValue: String)
