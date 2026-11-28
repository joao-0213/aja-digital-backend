package br.com.ajadigital.core.identity.application.contracts

/** Published pure identity validation for consumers that accept email addresses. */
object EmailAddress {
    fun isValid(value: String): Boolean = br.com.ajadigital.core.identity.domain.policies.EmailAddress
        .isValid(value)
}
