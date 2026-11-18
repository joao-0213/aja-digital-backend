package br.com.ajadigital.core.identity.application.models



data class LoginCommand(val email: String, val password: String, val previousSessionCookie: String?)
