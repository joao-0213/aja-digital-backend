package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests

import jakarta.validation.constraints.NotBlank

data class PasswordResetRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val password: String,
)
