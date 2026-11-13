package br.com.ajadigital.core.application.identity

import br.com.ajadigital.core.domain.identity.User
import java.time.Instant


data class SessionResponse(val user: User, val expiresAt: Instant, val sessionId: String)
