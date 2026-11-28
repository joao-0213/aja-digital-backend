package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers.toResponse
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.responses.UserResponse
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SelfController(private val authenticateStudent: ResolveSessionInputPort) {
    @GetMapping("/api/v1/me")
    fun me(@CookieValue(name = "aja_session", required = false) cookie: String?): UserResponse = authenticateStudent.resolve(cookie).user.toResponse()
}
