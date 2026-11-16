package br.com.ajadigital.core.identity.adapters.`in`.controllers

import org.springframework.http.ResponseCookie
import java.time.Duration


internal fun createSessionCookie(value: String, maxAge: Duration, secure: Boolean): ResponseCookie =
    ResponseCookie.from("aja_session", value)
        .httpOnly(true)
        .secure(secure)
        .path("/api/v1")
        .sameSite("Lax")
        .maxAge(maxAge)
        .build()
