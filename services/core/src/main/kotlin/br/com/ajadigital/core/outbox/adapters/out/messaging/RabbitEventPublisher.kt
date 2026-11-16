package br.com.ajadigital.core.outbox.adapters.out.messaging

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.UnroutableOutboxEvent
import br.com.ajadigital.core.outbox.domain.ports.out.EventPublisher
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ReturnListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import tools.jackson.databind.ObjectMapper
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

class RabbitEventPublisher(
    private val rabbit: RabbitTemplate,
    private val mapper: ObjectMapper,
) : EventPublisher {
    override fun publish(event: PendingOutboxEvent) {
        val body = mapper.writeValueAsBytes(
            mapOf(
                "id" to event.id,
                "eventType" to event.type,
                "version" to event.version,
                "keyId" to event.keyId,
                "nonce" to Base64.getEncoder().encodeToString(event.nonce),
                "ciphertext" to Base64.getEncoder().encodeToString(event.ciphertext),
            ),
        )
        rabbit.execute { channel ->
            channel.confirmSelect()
            val returned = AtomicBoolean(false)
            val returnListener = ReturnListener { _, _, _, _, _, _ -> returned.set(true) }
            channel.addReturnListener(returnListener)
            try {
                channel.basicPublish(
                    IdentityEventsTopology.EVENTS_EXCHANGE,
                    event.type,
                    true,
                    AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json").build(),
                    body,
                )
                channel.waitForConfirmsOrDie(5000)
                if (returned.get()) throw UnroutableOutboxEvent()
            } finally {
                channel.removeReturnListener(returnListener)
            }
        }
    }
}
