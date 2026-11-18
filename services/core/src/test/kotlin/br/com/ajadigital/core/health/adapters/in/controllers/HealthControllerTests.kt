package br.com.ajadigital.core.health.adapters.`in`.controllers

import br.com.ajadigital.core.health.application.usecases.CheckServiceHealthUseCase
import br.com.ajadigital.core.health.domain.ports.out.HealthProbeOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthControllerTests {
    @Test
    fun healthReportsDependencyStateAndVersion() {
        var dependenciesAvailable = true
        val controller = HealthController(CheckServiceHealthUseCase(HealthProbeOutputPort { dependenciesAvailable }), "1")
        val healthy = controller.health()
        assertEquals(200, healthy.statusCode.value())
        assertEquals(mapOf("status" to "ok", "version" to "1"), healthy.body)
        dependenciesAvailable = false
        assertEquals(503, controller.health().statusCode.value())
    }
}
