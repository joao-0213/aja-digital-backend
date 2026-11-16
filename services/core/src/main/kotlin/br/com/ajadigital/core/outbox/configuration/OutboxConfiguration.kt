package br.com.ajadigital.core.outbox.configuration

import br.com.ajadigital.core.outbox.adapters.`in`.scheduling.OutboxScheduler
import br.com.ajadigital.core.outbox.adapters.out.messaging.RabbitEventPublisher
import br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxRepository
import br.com.ajadigital.core.outbox.adapters.out.security.AesGcmOutboxPayloadProtector
import br.com.ajadigital.core.outbox.domain.ports.`in`.PublishPendingEventsUseCase
import br.com.ajadigital.core.outbox.domain.ports.`in`.RedriveOutboxEventUseCase
import br.com.ajadigital.core.outbox.domain.ports.out.EventPublisher
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxPayloadProtector
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepository
import br.com.ajadigital.core.outbox.domain.usecases.PublishPendingEvents
import br.com.ajadigital.core.outbox.domain.usecases.RedriveOutboxEvent
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration
class OutboxConfiguration {
    @Bean
    fun outboxRepository(jdbc: JdbcTemplate): OutboxRepository = JdbcOutboxRepository(jdbc)

    @Bean
    fun eventPublisher(rabbit: RabbitTemplate, mapper: ObjectMapper): EventPublisher =
        RabbitEventPublisher(rabbit, mapper)

    @Bean
    fun outboxPayloadProtector(
        @Value("\${core.outbox.encryption-key}") keyBase64: String,
    ): OutboxPayloadProtector = AesGcmOutboxPayloadProtector(keyBase64)

    @Bean
    fun publishPendingEventsUseCase(
        transactions: TransactionRunner,
        repository: OutboxRepository,
        publisher: EventPublisher,
        clock: Clock,
    ): PublishPendingEventsUseCase = PublishPendingEvents(transactions, repository, publisher, clock)

    @Bean
    fun redriveOutboxEventUseCase(
        repository: OutboxRepository,
        clock: Clock,
    ): RedriveOutboxEventUseCase = RedriveOutboxEvent(repository, clock)

    @Bean
    fun outboxScheduler(publishPendingEvents: PublishPendingEventsUseCase): OutboxScheduler =
        OutboxScheduler(publishPendingEvents)
}
