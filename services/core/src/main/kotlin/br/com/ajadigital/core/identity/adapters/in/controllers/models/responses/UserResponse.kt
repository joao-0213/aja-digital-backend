package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses

import java.time.Instant

data class UserResponse(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val emailConfirmedAt: Instant?,
)
