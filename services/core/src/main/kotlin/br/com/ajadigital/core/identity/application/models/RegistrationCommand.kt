package br.com.ajadigital.core.identity.application.models



data class RegistrationCommand(val name: String, val email: String, val password: String)
