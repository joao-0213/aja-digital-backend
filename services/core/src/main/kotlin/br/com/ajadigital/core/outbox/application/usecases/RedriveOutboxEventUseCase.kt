package br.com.ajadigital.core.outbox.application.usecases

import br.com.ajadigital.core.outbox.application.ports.`in`.RedriveOutboxEventInputPort
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepositoryOutputPort
import br.com.ajadigital.core.outbox.domain.models.RedriveOutboxEventDto
import br.com.ajadigital.core.outbox.domain.models.RedriveStoredOutboxEventDto
import java.time.Clock
import java.time.Instant

class RedriveOutboxEventUseCase(
    private val repository: OutboxRepositoryOutputPort,
    private val clock: Clock,
) : RedriveOutboxEventInputPort {
    override fun redrive(request: RedriveOutboxEventDto): Boolean = repository.redrive(RedriveStoredOutboxEventDto(request.id, Instant.now(clock)))
}
