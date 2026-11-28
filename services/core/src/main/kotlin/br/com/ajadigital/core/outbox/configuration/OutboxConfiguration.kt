package br.com.ajadigital.core.outbox.configuration

import br.com.ajadigital.core.outbox.adapters.`in`.scheduling.OutboxScheduler
import br.com.ajadigital.core.outbox.adapters.out.messaging.RabbitEventPublisher
import br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxRepository
import br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxEventWriter
import br.com.ajadigital.core.outbox.adapters.out.security.AesGcmOutboxPayloadProtector
import br.com.ajadigital.core.outbox.application.ports.`in`.PublishPendingEventsInputPort
import br.com.ajadigital.core.outbox.application.ports.`in`.RedriveOutboxEventInputPort
import br.com.ajadigital.core.outbox.application.ports.`in`.OutboxEventProducerInputPort
import br.com.ajadigital.core.outbox.application.ports.out.EventPublisherOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.InsertOutboxEventOutputPort
import br.com.ajadigital.core.outbox.application.ports.out.OutboxRepositoryOutputPort
import br.com.ajadigital.core.outbox.application.usecases.PublishPendingEventsUseCase
import br.com.ajadigital.core.outbox.application.usecases.RedriveOutboxEventUseCase
import br.com.ajadigital.core.outbox.application.usecases.OutboxEventProducerUseCase
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
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
    fun outboxRepository(jdbc: JdbcTemplate): OutboxRepositoryOutputPort = JdbcOutboxRepository(jdbc)

    @Bean
    fun outboxEventWriter(jdbc: JdbcTemplate): InsertOutboxEventOutputPort = JdbcOutboxEventWriter(jdbc)

    @Bean
    fun outboxEventProducer(protector: OutboxPayloadProtectorOutputPort, events: InsertOutboxEventOutputPort): OutboxEventProducerInputPort =
        OutboxEventProducerUseCase(protector, events)

    @Bean
    fun eventPublisher(rabbit: RabbitTemplate, mapper: ObjectMapper): EventPublisherOutputPort =
        RabbitEventPublisher(rabbit, mapper)

    @Bean
    fun outboxPayloadProtector(
        @Value("\${core.outbox.encryption-key}") keyBase64: String,
        mapper: ObjectMapper,
    ): OutboxPayloadProtectorOutputPort = AesGcmOutboxPayloadProtector(keyBase64, mapper)

    @Bean
    fun publishPendingEventsUseCase(
        transactions: TransactionRunnerOutputPort,
        repository: OutboxRepositoryOutputPort,
        publisher: EventPublisherOutputPort,
        clock: Clock,
    ): PublishPendingEventsInputPort = PublishPendingEventsUseCase(transactions, repository, publisher, clock)

    @Bean
    fun redriveOutboxEventUseCase(
        repository: OutboxRepositoryOutputPort,
        clock: Clock,
    ): RedriveOutboxEventInputPort = RedriveOutboxEventUseCase(repository, clock)

    @Bean
    fun outboxScheduler(publishPendingEvents: PublishPendingEventsInputPort): OutboxScheduler =
        OutboxScheduler(publishPendingEvents)
}
