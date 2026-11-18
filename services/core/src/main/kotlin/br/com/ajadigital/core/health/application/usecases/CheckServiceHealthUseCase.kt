package br.com.ajadigital.core.health.application.usecases

import br.com.ajadigital.core.health.application.ports.`in`.CheckServiceHealthInputPort
import br.com.ajadigital.core.health.domain.ports.out.HealthProbeOutputPort
import br.com.ajadigital.core.health.domain.models.HealthProbeDto
import br.com.ajadigital.core.health.domain.models.ServiceReadiness

class CheckServiceHealthUseCase(private val probe: HealthProbeOutputPort) : CheckServiceHealthInputPort {
    override operator fun invoke(request: HealthProbeDto) = ServiceReadiness(probe.dependenciesAvailable(request))
}
