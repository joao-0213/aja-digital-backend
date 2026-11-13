package br.com.ajadigital.core.application.identity



data class RegistrationCommand(val name: String, val email: String, val password: String)
