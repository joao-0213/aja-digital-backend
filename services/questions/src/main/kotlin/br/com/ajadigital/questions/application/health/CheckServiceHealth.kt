package br.com.ajadigital.questions.application.health

import br.com.ajadigital.questions.domain.health.ServiceReadiness
import br.com.ajadigital.questions.ports.out.HealthProbe

class CheckServiceHealth(
    private val probe: HealthProbe,
) {
    operator fun invoke() = ServiceReadiness(probe.dependenciesAvailable())
}
