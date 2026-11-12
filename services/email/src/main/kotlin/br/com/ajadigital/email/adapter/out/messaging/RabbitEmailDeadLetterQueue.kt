package br.com.ajadigital.email.adapter.out.messaging

import br.com.ajadigital.email.ports.out.EmailDeadLetterQueue
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.GetResponse
import com.rabbitmq.client.Channel
import org.springframework.beans.factory.ObjectProvider
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

private const val EVENTS_QUEUE = "email.identity.events"
private const val DEAD_LETTER_QUEUE = "email.identity.dlq"

fun interface RedriveConfirmationHook {
    fun afterConfirmed(eventId: String, originalProperties: AMQP.BasicProperties, channel: Channel)
}

@Component
class RabbitEmailDeadLetterQueue(
    private val rabbit: RabbitTemplate,
    private val mapper: ObjectMapper,
    private val confirmationHooks: ObjectProvider<RedriveConfirmationHook>,
) : EmailDeadLetterQueue {
    override fun republish(eventId: String): Boolean = rabbit.execute { channel ->
        val count = channel.messageCount(DEAD_LETTER_QUEUE).toInt()
        val held = mutableListOf<GetResponse>()
        repeat(count) { channel.basicGet(DEAD_LETTER_QUEUE, false)?.let(held::add) }
        val matching = held.filter { response ->
            runCatching { mapper.readTree(response.body).path("id").asText() == eventId }.getOrDefault(false)
        }
        if (matching.isEmpty() || matching.any { !it.body.contentEquals(matching.first().body) }) {
            held.forEach { channel.basicNack(it.envelope.deliveryTag, false, true) }
            return@execute false
        }

        held.filterNot { it in matching }.forEach { channel.basicNack(it.envelope.deliveryTag, false, true) }
        channel.confirmSelect()
        val target = matching.first()
        publish(channel, EVENTS_QUEUE, target)
        channel.waitForConfirmsOrDie(5000)
        confirmationHooks.ifAvailable?.afterConfirmed(eventId, target.props, channel)
        matching.forEach { channel.basicAck(it.envelope.deliveryTag, false) }
        true
    } ?: false

    private fun publish(channel: com.rabbitmq.client.Channel, queue: String, response: GetResponse) {
        channel.basicPublish(
            "", queue, false,
            response.props,
            response.body,
        )
    }
}
