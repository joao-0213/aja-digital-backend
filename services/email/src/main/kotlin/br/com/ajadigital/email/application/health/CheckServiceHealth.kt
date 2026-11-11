package br.com.ajadigital.email.application.health

import br.com.ajadigital.email.ports.out.HealthProbe
import br.com.ajadigital.email.domain.health.ServiceReadiness

class CheckServiceHealth(private val probe: HealthProbe) {
    operator fun invoke() = ServiceReadiness(probe.dependenciesAvailable())
}
