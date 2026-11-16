package br.com.ajadigital.core.health.domain.usecases

import br.com.ajadigital.core.health.domain.ports.out.HealthProbe
import br.com.ajadigital.core.health.domain.models.ServiceReadiness

class CheckServiceHealth(private val probe: HealthProbe) {
    operator fun invoke() = ServiceReadiness(probe.dependenciesAvailable())
}
