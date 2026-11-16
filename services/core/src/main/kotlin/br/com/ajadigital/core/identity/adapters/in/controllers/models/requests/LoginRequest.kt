package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank val email: String,
    val password: String,
)
