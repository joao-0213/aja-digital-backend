package br.com.ajadigital.core.application.identity



data class LoginCommand(val email: String, val password: String, val previousSessionCookie: String?)
