package br.com.ajadigital.core.identity.application.models

data class ResetIdentityPasswordDto(
    val tokenHash: String,
    val passwordHash: String,
)
