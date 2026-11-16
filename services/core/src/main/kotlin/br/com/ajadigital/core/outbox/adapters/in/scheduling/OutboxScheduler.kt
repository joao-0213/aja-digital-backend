package br.com.ajadigital.core.outbox.adapters.`in`.scheduling

import br.com.ajadigital.core.outbox.domain.ports.`in`.PublishPendingEventsUseCase
import org.springframework.scheduling.annotation.Scheduled

class OutboxScheduler(
    private val publishPendingEvents: PublishPendingEventsUseCase,
) {
    @Scheduled(fixedDelayString = "\${core.outbox.poll-interval-ms:500}")
    fun publishAvailable() {
        publishPendingEvents.publishAvailable()
    }
}
