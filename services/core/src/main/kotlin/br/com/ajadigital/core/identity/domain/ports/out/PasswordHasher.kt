package br.com.ajadigital.core.identity.domain.ports.out

interface PasswordHasher {
    fun hash(plaintext: String): String
    fun matches(plaintext: String, encoded: String): Boolean
}
