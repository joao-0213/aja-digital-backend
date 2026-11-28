package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses

import java.time.Instant

data class SessionResponse(val user: UserResponse, val expiresAt: Instant, val sessionId: String)
