package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicActor
import br.com.ajadigital.core.application.academic.ManageDashboard
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(private val auth: AuthenticateStudent, private val dashboard: ManageDashboard) {
    @GetMapping("/api/v1/me/dashboard")
    fun dashboard(@CookieValue(name = "aja_session", required = false) cookie: String?) = dashboard.dashboard(actor(cookie))

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
