package br.com.ajadigital.core.application.identity



data class PasswordResetCommand(val token: String, val password: String)
