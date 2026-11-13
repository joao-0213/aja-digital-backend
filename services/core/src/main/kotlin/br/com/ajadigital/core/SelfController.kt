package br.com.ajadigital.core

import br.com.ajadigital.core.application.identity.AuthenticateStudent
import br.com.ajadigital.core.domain.identity.User
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping


@RestController
class SelfController(private val authenticateStudent: AuthenticateStudent) {
    @GetMapping("/api/v1/me")
    fun me(@CookieValue(name = "aja_session", required = false) cookie: String?): User =
        authenticateStudent.resolve(cookie).user
}
