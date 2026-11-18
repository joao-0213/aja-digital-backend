package br.com.ajadigital.core.identity.application.models



data class PasswordResetCommand(val token: String, val password: String)
