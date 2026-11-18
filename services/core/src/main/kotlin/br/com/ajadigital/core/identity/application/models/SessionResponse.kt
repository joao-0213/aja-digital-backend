package br.com.ajadigital.core.identity.application.models

import br.com.ajadigital.core.identity.domain.models.User
import java.time.Instant


data class SessionResponse(val user: User, val expiresAt: Instant, val sessionId: String)
