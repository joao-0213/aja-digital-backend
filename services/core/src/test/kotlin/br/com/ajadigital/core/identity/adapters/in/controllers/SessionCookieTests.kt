package br.com.ajadigital.core.identity.adapters.`in`.controllers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SessionCookieTests {
    @Test
    fun secureFlagFollowsExplicitTlsConfiguration() {
        val httpsCookie = createSessionCookie("opaque", Duration.ofHours(8), secure = true).toString()
        val localHttpCookie = createSessionCookie("opaque", Duration.ofHours(8), secure = false).toString()

        assertTrue(httpsCookie.contains("; Secure"))
        assertFalse(localHttpCookie.contains("; Secure"))
    }
}
