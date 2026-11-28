package br.com.ajadigital.core.identity.domain.policies

object EmailAddress {
    private val format = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValid(value: String): Boolean = value.length <= MAX_EMAIL_LENGTH && format.matches(value)
}

private const val MAX_EMAIL_LENGTH = 254
