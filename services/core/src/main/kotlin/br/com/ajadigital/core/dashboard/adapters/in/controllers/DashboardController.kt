package br.com.ajadigital.core.dashboard.adapters.`in`.controllers
import br.com.ajadigital.core.dashboard.application.models.*

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.dashboard.application.ports.`in`.GetDashboardInputPort
import br.com.ajadigital.core.dashboard.application.models.GetDashboardDto
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(private val auth: ResolveAuthenticatedActorInputPort, private val dashboard: GetDashboardInputPort) {
    @GetMapping("/api/v1/me/dashboard")
    fun dashboard(@CookieValue(name = "aja_session", required = false) cookie: String?) = dashboard.dashboard(GetDashboardDto(actor(cookie)))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
}
