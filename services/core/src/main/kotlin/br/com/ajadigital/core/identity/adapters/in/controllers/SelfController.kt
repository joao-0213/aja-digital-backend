package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.identity.domain.models.User
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping


@RestController
class SelfController(private val authenticateStudent: AuthenticateStudentUseCase) {
    @GetMapping("/api/v1/me")
    fun me(@CookieValue(name = "aja_session", required = false) cookie: String?): User =
        authenticateStudent.resolve(cookie).user
}
