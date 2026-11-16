package br.com.ajadigital.core.identity.domain.policies

object EmailAddress {
    private val format = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValid(value: String): Boolean = value.length <= 254 && format.matches(value)
}
