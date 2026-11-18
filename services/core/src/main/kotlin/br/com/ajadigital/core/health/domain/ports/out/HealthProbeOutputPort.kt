package br.com.ajadigital.core.health.domain.ports.out

import br.com.ajadigital.core.health.domain.models.HealthProbeDto

fun interface HealthProbeOutputPort {
    fun dependenciesAvailable(request: HealthProbeDto): Boolean
}
