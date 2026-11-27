package br.com.ajadigital.core.outbox.application.ports.`in`

import br.com.ajadigital.core.outbox.domain.models.RedriveOutboxEventDto

interface RedriveOutboxEventInputPort {
    fun redrive(request: RedriveOutboxEventDto): Boolean
}
