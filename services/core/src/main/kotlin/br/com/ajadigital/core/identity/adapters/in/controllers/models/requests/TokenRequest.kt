package br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests

import jakarta.validation.constraints.NotBlank

data class TokenRequest(
    @field:NotBlank val token: String,
)
