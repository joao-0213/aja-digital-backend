package br.com.ajadigital.core.outbox.application.ports.`in`

import br.com.ajadigital.core.outbox.domain.models.PublishAvailableDto

interface PublishPendingEventsInputPort {
    fun publishAvailable(request: PublishAvailableDto)
}
