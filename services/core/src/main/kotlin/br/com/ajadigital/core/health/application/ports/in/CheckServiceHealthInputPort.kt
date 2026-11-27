package br.com.ajadigital.core.health.application.ports.`in`

import br.com.ajadigital.core.health.domain.models.ServiceReadiness

interface CheckServiceHealthInputPort {
    operator fun invoke(): ServiceReadiness
}
