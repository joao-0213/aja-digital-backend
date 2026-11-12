package br.com.ajadigital.email.adapter.`in`.messaging

import br.com.ajadigital.email.application.delivery.IdentityEmail
import br.com.ajadigital.email.application.delivery.ProcessIdentityEmail
import br.com.ajadigital.email.ports.out.DeliveryOutcome
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import com.rabbitmq.client.Channel
import com.rabbitmq.client.AMQP
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val EMAIL_EVENTS_QUEUE = "email.identity.events"
private const val EMAIL_RETRY_QUEUE = "email.identity.retry"
private const val EMAIL_DEAD_LETTER_QUEUE = "email.identity.dlq"

@Configuration
class IdentityEventTopology(@Value("\${email.retry.delay-ms:5000}") private val retryDelayMs: Int) {
    @Bean fun identityEventsExchange() = TopicExchange("aja.events", true, false)
    @Bean fun identityEventsQueue() = QueueBuilder.durable(EMAIL_EVENTS_QUEUE)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", EMAIL_DEAD_LETTER_QUEUE)
        .build()
    @Bean fun identityRetryQueue() = QueueBuilder.durable(EMAIL_RETRY_QUEUE)
        .withArgument("x-message-ttl", retryDelayMs)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", EMAIL_EVENTS_QUEUE)
        .build()
    @Bean fun identityDeadLetterQueue() = QueueBuilder.durable(EMAIL_DEAD_LETTER_QUEUE).build()
    @Bean fun confirmationBinding(exchange: TopicExchange) = BindingBuilder.bind(identityEventsQueue()).to(exchange)
        .with("identity.email-confirmation-requested")
    @Bean fun resetBinding(exchange: TopicExchange) = BindingBuilder.bind(identityEventsQueue()).to(exchange)
        .with("identity.password-reset-requested")
}

@Component
class IdentityEventConsumer(
    private val rabbit: RabbitTemplate,
    private val mapper: ObjectMapper,
    private val process: ProcessIdentityEmail,
    private val deliveryStore: EmailDeliveryStore,
    @Value("\${email.outbox.encryption-key}") keyBase64: String,
) {
    private val key = Base64.getDecoder().decode(keyBase64).also { require(it.size == 32) }
    private val keyId = MessageDigest.getInstance("SHA-256").digest(key).take(8).joinToString("") { "%02x".format(it) }

    @RabbitListener(queues = [EMAIL_EVENTS_QUEUE], ackMode = "MANUAL")
    fun consume(message: Message, channel: Channel) {
        val tag = message.messageProperties.deliveryTag
        val email = try { decode(message.body) } catch (_: Exception) {
            runCatching {
                val envelope = mapper.readTree(message.body)
                val id = envelope.path("id").asText().takeIf(String::isNotBlank)
                if (id != null) deliveryStore.recordPoison(id, envelope.path("eventType").asText("unknown"))
            }
            channel.basicNack(tag, false, false)
            return
        }
        when (process.process(email)) {
            DeliveryOutcome.ACKNOWLEDGED -> channel.basicAck(tag, false)
            DeliveryOutcome.DEAD -> channel.basicNack(tag, false, false)
            DeliveryOutcome.RETRY -> {
                rabbit.execute { retryChannel ->
                    retryChannel.confirmSelect()
                    retryChannel.basicPublish(
                        "", EMAIL_RETRY_QUEUE, false,
                        AMQP.BasicProperties.Builder().deliveryMode(2).contentType("application/json").build(),
                        message.body,
                    )
                    retryChannel.waitForConfirmsOrDie(5000)
                }
                channel.basicAck(tag, false)
            }
        }
    }

    private fun decode(body: ByteArray): IdentityEmail {
        val envelope = mapper.readTree(body)
        val id = envelope.path("id").asText().takeIf(String::isNotBlank) ?: error("missing event id")
        val type = envelope.path("eventType").asText()
        require(type == "identity.email-confirmation-requested" || type == "identity.password-reset-requested")
        require(envelope.path("version").asInt() == 1 && envelope.path("keyId").asText() == keyId)
        val nonce = Base64.getDecoder().decode(envelope.path("nonce").asText())
        val ciphertext = Base64.getDecoder().decode(envelope.path("ciphertext").asText())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("$id|$type|1".toByteArray())
        val payload = mapper.readTree(cipher.doFinal(ciphertext))
        val purpose = payload.path("purpose").asText()
        require(purpose == if (type == "identity.email-confirmation-requested") "email_confirmation" else "password_reset")
        val recipient = payload.path("recipient").asText().takeIf(String::isNotBlank) ?: error("missing recipient")
        val token = payload.path("token").asText().takeIf(String::isNotBlank) ?: error("missing token")
        return IdentityEmail(id, type, recipient, token, purpose)
    }
}
