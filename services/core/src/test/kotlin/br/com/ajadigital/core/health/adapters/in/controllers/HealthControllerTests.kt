package br.com.ajadigital.core.health.adapters.`in`.controllers

import br.com.ajadigital.core.health.application.usecases.CheckServiceHealthUseCase
import br.com.ajadigital.core.health.application.ports.out.HealthProbeOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HealthControllerTests {
    @Test
    fun healthReportsDependencyStateAndVersion() {
        var dependenciesAvailable = true
        val controller = HealthController(CheckServiceHealthUseCase(HealthProbeOutputPort { dependenciesAvailable }), "1")
        val mvc = MockMvcBuilders.standaloneSetup(controller).build()
        mvc.get("/api/v1/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ok") }
            jsonPath("$.version") { value("1") }
        }
        dependenciesAvailable = false
        mvc.get("/api/v1/health").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.error.code") { value("API_UNAVAILABLE") }
            jsonPath("$.error.message") { value("Service dependencies are unavailable") }
            jsonPath("$.error.requestId") { isNotEmpty() }
        }
    }
}
