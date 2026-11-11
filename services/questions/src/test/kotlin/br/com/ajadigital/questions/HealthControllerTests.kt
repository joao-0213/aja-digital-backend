package br.com.ajadigital.questions

import br.com.ajadigital.questions.application.health.CheckServiceHealth
import br.com.ajadigital.questions.ports.out.HealthProbe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthControllerTests {
    @Test
    fun healthReportsDependencyStateAndVersion() {
        var dependenciesAvailable = true
        val controller = HealthController(CheckServiceHealth(HealthProbe { dependenciesAvailable }), "1")
        val healthy = controller.health()
        assertEquals(200, healthy.statusCode.value())
        assertEquals(mapOf("status" to "ok", "version" to "1"), healthy.body)
        dependenciesAvailable = false
        assertEquals(503, controller.health().statusCode.value())
    }
}
