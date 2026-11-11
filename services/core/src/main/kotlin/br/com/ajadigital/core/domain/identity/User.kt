package br.com.ajadigital.core.domain.identity

import java.time.Instant

data class User(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String,
    val name: String,
    val email: String,
    val role: String = "student",
    val active: Boolean = true,
    val emailConfirmedAt: Instant? = null,
)
