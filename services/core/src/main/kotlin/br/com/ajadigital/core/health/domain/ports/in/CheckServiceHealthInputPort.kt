package br.com.ajadigital.core.health.application.ports.`in`

import br.com.ajadigital.core.health.domain.models.ServiceReadiness
import br.com.ajadigital.core.health.domain.models.HealthProbeDto

interface CheckServiceHealthInputPort {
    operator fun invoke(request: HealthProbeDto): ServiceReadiness
}
