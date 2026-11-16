package br.com.ajadigital.core.health.domain.ports.`in`

import br.com.ajadigital.core.health.domain.models.ServiceReadiness

interface CheckServiceHealthUseCase {
    operator fun invoke(): ServiceReadiness
}
