package br.com.ajadigital.core

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration


@TestConfiguration
class EmailQueueTopologyTestConfiguration {
    @Bean
    fun emailQueues(exchange: TopicExchange): Declarables {
        val eventQueue = QueueBuilder.durable("email.identity.events")
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", "email.identity.dlq")
            .build()
        val retryQueue = QueueBuilder.durable("email.identity.retry")
            .withArgument("x-message-ttl", 5000)
            .withArgument("x-dead-letter-exchange", "")
            .withArgument("x-dead-letter-routing-key", "email.identity.events")
            .build()
        val deadLetterQueue = QueueBuilder.durable("email.identity.dlq").build()
        return Declarables(
            eventQueue,
            retryQueue,
            deadLetterQueue,
            BindingBuilder.bind(eventQueue).to(exchange).with("identity.email-confirmation-requested"),
            BindingBuilder.bind(eventQueue).to(exchange).with("identity.password-reset-requested"),
            BindingBuilder.bind(eventQueue).to(exchange).with("academic.announcement-notification-requested"),
        )
    }
}
