package br.com.ajadigital.core.identity.application.ports.`in`

interface HashPasswordInputPort {
    fun hash(password: String): String
}
