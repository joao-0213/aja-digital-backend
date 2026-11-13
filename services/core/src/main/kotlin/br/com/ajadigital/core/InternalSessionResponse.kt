package br.com.ajadigital.core



data class InternalSessionResponse(val userId: String, val sessionId: String, val role: String, val active: Boolean, val emailConfirmedAt: java.time.Instant?)
