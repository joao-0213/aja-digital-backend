package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)
