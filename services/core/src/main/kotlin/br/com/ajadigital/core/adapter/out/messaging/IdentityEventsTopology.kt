package br.com.ajadigital.core.adapter.out.messaging

import org.springframework.amqp.core.TopicExchange
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IdentityEventsTopology {
    @Bean
    fun identityEventsExchange() = TopicExchange(EVENTS_EXCHANGE, true, false)
}

const val EVENTS_EXCHANGE = "aja.events"
