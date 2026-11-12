package br.com.ajadigital.core.adapter.out.messaging

import org.springframework.amqp.rabbit.core.RabbitTemplate
import com.rabbitmq.client.AMQP
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private class UnroutableOutboxEvent : RuntimeException()

private data class PendingOutboxEvent(
    val id: String,
    val type: String,
    val version: Int,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val attempts: Int,
)

@Component
class OutboxPublisher(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val rabbit: RabbitTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
) {
    @Scheduled(fixedDelayString = "\${core.outbox.poll-interval-ms:500}")
    fun publishAvailable() {
        transactions.executeWithoutResult {
            val now = Instant.now(clock)
            val events = jdbc.query(
                """select id,event_type,event_version,key_id,nonce,encrypted_payload,attempt_count
                   from core.outbox_events where published_at is null and failed_at is null and next_attempt_at<=?
                   order by created_at for update skip locked limit 25""",
                { rs, _ -> PendingOutboxEvent(
                    rs.getString("id"), rs.getString("event_type"), rs.getInt("event_version"), rs.getString("key_id"),
                    rs.getBytes("nonce"), rs.getBytes("encrypted_payload"), rs.getInt("attempt_count"),
                ) },
                Timestamp.from(now),
            )
            events.forEach { event -> publishOne(event, now) }
        }
    }

    private fun publishOne(event: PendingOutboxEvent, now: Instant) {
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
        val failure = try {
            rabbit.execute { channel ->
                channel.confirmSelect()
                val returned = AtomicBoolean(false)
                val returnListener = com.rabbitmq.client.ReturnListener { _, _, _, _, _, _ -> returned.set(true) }
                channel.addReturnListener(returnListener)
                try {
                    channel.basicPublish(
                        EVENTS_EXCHANGE,
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
            null
        } catch (error: Exception) {
            error
        }
        if (failure == null) {
            // A crash after RabbitMQ confirms but before this update is an intentional at-least-once redelivery window.
            jdbc.update("update core.outbox_events set published_at=?,attempt_count=attempt_count+1,last_error=null where id=?", Timestamp.from(now), event.id)
        } else {
            val attempts = event.attempts + 1
            val delaySeconds = (1L shl (attempts - 1).coerceAtMost(8)).coerceAtMost(300)
            jdbc.update(
                """update core.outbox_events set attempt_count=?,next_attempt_at=?,last_error=?,failed_at=? where id=?""",
                attempts,
                Timestamp.from(now.plusSeconds(delaySeconds)),
                failure.javaClass.simpleName.take(80),
                if (attempts >= MAX_ATTEMPTS) Timestamp.from(now) else null,
                event.id,
            )
        }
    }

    companion object { private const val MAX_ATTEMPTS = 5 }
}

@Component
class OutboxRedrive(private val jdbc: JdbcTemplate, private val clock: Clock) {
    fun redrive(id: String): Boolean = jdbc.update(
        """update core.outbox_events set attempt_count=0,next_attempt_at=?,last_error=null,failed_at=null
           where id=? and published_at is null and failed_at is not null""",
        Timestamp.from(Instant.now(clock)), id,
    ) == 1
}

@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/internal/ops/outbox")
class OutboxOperationsController(
    private val redrive: OutboxRedrive,
    @org.springframework.beans.factory.annotation.Value("\${core.outbox.operator-token}") private val operatorToken: String,
) {
    init {
        require(operatorToken.isNotBlank()) { "Core outbox operator token must not be blank" }
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/redrive")
    fun redrive(
        @org.springframework.web.bind.annotation.PathVariable id: String,
        @org.springframework.web.bind.annotation.RequestHeader("X-AJA-Operator-Token", required = false) suppliedToken: String?,
    ): org.springframework.http.ResponseEntity<Void> {
        if (suppliedToken.isNullOrBlank() || !java.security.MessageDigest.isEqual(operatorToken.toByteArray(), suppliedToken.toByteArray())) {
            return org.springframework.http.ResponseEntity.status(401).build()
        }
        if (!runCatching { UUID.fromString(id) }.isSuccess || !redrive.redrive(id)) return org.springframework.http.ResponseEntity.notFound().build()
        return org.springframework.http.ResponseEntity.accepted().build()
    }
}
