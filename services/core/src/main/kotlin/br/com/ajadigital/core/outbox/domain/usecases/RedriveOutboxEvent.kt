package br.com.ajadigital.core.outbox.domain.usecases

import br.com.ajadigital.core.outbox.domain.ports.`in`.RedriveOutboxEventUseCase
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepository
import java.time.Clock
import java.time.Instant

class RedriveOutboxEvent(
    private val repository: OutboxRepository,
    private val clock: Clock,
) : RedriveOutboxEventUseCase {
    override fun redrive(id: String): Boolean = repository.redrive(id, Instant.now(clock))
}
