package br.com.ajadigital.core.identity.domain.models



data class PasswordResetCommand(val token: String, val password: String)
