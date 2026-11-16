package br.com.ajadigital.core.dashboard.adapters.`in`.controllers

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.dashboard.domain.ports.`in`.GetDashboardUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(private val auth: AuthenticateStudentUseCase, private val dashboard: GetDashboardUseCase) {
    @GetMapping("/api/v1/me/dashboard")
    fun dashboard(@CookieValue(name = "aja_session", required = false) cookie: String?) = dashboard.dashboard(actor(cookie))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
