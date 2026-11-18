package br.com.ajadigital.core.outbox.adapters.`in`.scheduling

import br.com.ajadigital.core.outbox.application.ports.`in`.PublishPendingEventsInputPort
import br.com.ajadigital.core.outbox.domain.models.PublishAvailableDto
import org.springframework.scheduling.annotation.Scheduled

class OutboxScheduler(
    private val publishPendingEvents: PublishPendingEventsInputPort,
) {
    @Scheduled(fixedDelayString = "\${core.outbox.poll-interval-ms:500}")
    fun publishAvailable() {
        publishPendingEvents.publishAvailable(PublishAvailableDto)
    }
}
