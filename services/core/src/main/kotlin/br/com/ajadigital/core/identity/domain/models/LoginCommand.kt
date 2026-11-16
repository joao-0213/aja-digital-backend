package br.com.ajadigital.core.identity.domain.models



data class LoginCommand(val email: String, val password: String, val previousSessionCookie: String?)
