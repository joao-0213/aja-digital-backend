package br.com.ajadigital.questions.application.health

import br.com.ajadigital.questions.ports.out.HealthProbe
import br.com.ajadigital.questions.domain.health.ServiceReadiness

class CheckServiceHealth(private val probe: HealthProbe) {
    operator fun invoke() = ServiceReadiness(probe.dependenciesAvailable())
}
