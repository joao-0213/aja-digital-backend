package br.com.ajadigital.core.identity.application.models

data class RequestAuthTokenDto(
    val emailInput: String,
    val purpose: String,
)
