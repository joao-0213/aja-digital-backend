package br.com.ajadigital.core.academic.application.capabilities

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object AcademicTokens {
    fun generate(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
