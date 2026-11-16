package br.com.ajadigital.core.outbox.adapters.out.messaging

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdentityEventsTopology {
    @Bean
    fun identityEventsExchange() = TopicExchange(EVENTS_EXCHANGE, true, false)

    companion object {
        const val EVENTS_EXCHANGE = "aja.events"
    }
}
