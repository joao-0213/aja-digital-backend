package br.com.ajadigital.core.application.health

import br.com.ajadigital.core.ports.out.HealthProbe
import br.com.ajadigital.core.domain.health.ServiceReadiness

class CheckServiceHealth(private val probe: HealthProbe) {
    operator fun invoke() = ServiceReadiness(probe.dependenciesAvailable())
}
