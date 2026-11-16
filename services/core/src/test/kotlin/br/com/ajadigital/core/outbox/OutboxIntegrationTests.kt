package br.com.ajadigital.core.outbox
import br.com.ajadigital.core.CoreIntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.post
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit
import br.com.ajadigital.core.outbox.adapters.`in`.controllers.OutboxOperationsController
import br.com.ajadigital.core.outbox.adapters.out.persistence.JdbcOutboxRepository
import br.com.ajadigital.core.outbox.domain.usecases.RedriveOutboxEvent

class OutboxIntegrationTests : CoreIntegrationTestBase() {
    @Test
    fun outboxOperatorRejectsBlankConfiguredSecret() {
        assertThrows(IllegalArgumentException::class.java) {
            OutboxOperationsController(RedriveOutboxEvent(JdbcOutboxRepository(jdbc), mutableClock), " \t")
        }
        assertEquals(401, OutboxOperationsController(RedriveOutboxEvent(JdbcOutboxRepository(jdbc), mutableClock), "a-valid-secret")
            .redrive("not-a-uuid", " \t").statusCode.value())
    }

    @Test
    fun failedOutboxEventRedriveRequiresOperatorSecretAndPreservesCiphertext() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 3 }
        val ciphertext = ByteArray(48) { 9 }
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,
               attempt_count,next_attempt_at,last_error,failed_at) values(?, ?, 1, now(), 'fixture-key', ?, ?, 5, now(), 'IOException', now())""",
            id, "identity.email-confirmation-requested", nonce, ciphertext,
        )

        mvc.post("/internal/ops/outbox/$id/redrive") {
            header("Origin", "https://aja.example.test")
            header("X-AJA-Operator-Token", "wrong-operator-token")
        }.andExpect { status { isUnauthorized() } }
        mvc.post("/internal/ops/outbox/$id/redrive") {
            header("Origin", "https://aja.example.test")
            header("X-AJA-Operator-Token", "fixture-outbox-operator-token-1234567890")
        }.andExpect { status { isAccepted() } }

        val row = jdbc.queryForMap("select nonce,encrypted_payload,attempt_count,failed_at from core.outbox_events where id=?", id)
        assertTrue(nonce.contentEquals(row["nonce"] as ByteArray))
        assertTrue(ciphertext.contentEquals(row["encrypted_payload"] as ByteArray))
        assertEquals(0, row["attempt_count"])
        assertEquals(null, row["failed_at"])
    }

    @Test
    fun publisherRepublishesConfirmedEventWhenOutboxMarkingFails() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 5 }
        val ciphertext = ByteArray(48) { 8 }
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,next_attempt_at)
               values(?, 'identity.email-confirmation-requested', 1, now(), 'fixture-key', ?, ?, ?)""",
            id, nonce, ciphertext, java.sql.Timestamp.from(mutableClock.instant().plusSeconds(30)),
        )
        try {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("create sequence core.b03_outbox_failure_once")
                    statement.execute("grant usage, select on sequence core.b03_outbox_failure_once to core")
                    statement.execute(
                        """create function core.b03_fail_outbox_mark_once() returns trigger language plpgsql as ${'$'}${'$'}
                           begin
                             if new.id = '$id' and old.published_at is null and new.published_at is not null
                               and nextval('core.b03_outbox_failure_once') = 1 then
                               raise exception 'one-shot test failure after broker confirm';
                             end if;
                             return new;
                           end;
                           ${'$'}${'$'}""",
                    )
                    statement.execute(
                        "create trigger b03_fail_outbox_mark_once before update on core.outbox_events for each row execute function core.b03_fail_outbox_mark_once()",
                    )
                }
            }
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)

            val publishedBodies = mutableListOf<String>()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
            while (publishedBodies.size < 2 && System.nanoTime() < deadline) {
                val body = rabbitTemplate.receive("email.identity.events", 250)?.body?.let(::String) ?: continue
                if (Regex(""""id":"([^"]+)"""").find(body)?.groupValues?.get(1) == id) publishedBodies += body
            }

            assertEquals(2, publishedBodies.size, "Expected redelivery after confirmed publish could not be marked in PostgreSQL")
            assertEquals(publishedBodies.first(), publishedBodies.last(), "At-least-once retry must preserve the same event id and encrypted payload")
            assertEquals(true, jdbc.queryForObject("select published_at is not null from core.outbox_events where id=?", Boolean::class.java, id))
            assertEquals(1, jdbc.queryForObject("select attempt_count from core.outbox_events where id=?", Int::class.java, id))
        } finally {
            DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("drop trigger if exists b03_fail_outbox_mark_once on core.outbox_events")
                    statement.execute("drop function if exists core.b03_fail_outbox_mark_once()")
                    statement.execute("drop sequence if exists core.b03_outbox_failure_once")
                }
            }
        }
    }

    @Test
    fun publisherKeepsOutboxPendingUntilEmailQueueIsBound() {
        val id = UUID.randomUUID().toString()
        val nonce = ByteArray(12) { 6 }
        val ciphertext = ByteArray(48) { 7 }
        val future = java.sql.Timestamp.from(mutableClock.instant().plusSeconds(30))
        jdbc.update(
            """insert into core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload,next_attempt_at)
               values(?, 'identity.email-confirmation-requested', 1, now(), 'fixture-key', ?, ?, ?)""",
            id, nonce, ciphertext, future,
        )

        try {
            rabbitTemplate.execute { channel ->
                listOf("email.identity.events", "email.identity.retry", "email.identity.dlq").forEach(channel::queueDelete)
            }
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)
            outboxPublisher.publishAvailable()

            assertEquals(null, jdbc.queryForObject("select published_at from core.outbox_events where id=?", java.sql.Timestamp::class.java, id),
                "An unroutable publisher-confirmed event must remain pending")
            assertEquals(1, jdbc.queryForObject("select attempt_count from core.outbox_events where id=?", Int::class.java, id))
            assertEquals(true, jdbc.queryForObject("select last_error is not null from core.outbox_events where id=?", Boolean::class.java, id),
                "The broker return must be recorded as an observable publish failure")

            declareEmailQueueTopology()
            jdbc.update("update core.outbox_events set next_attempt_at=? where id=?", java.sql.Timestamp.from(mutableClock.instant()), id)
            outboxPublisher.publishAvailable()
            awaitPublished(id)
            assertTrue(receivePublishedEvent(id).contains(id), "Event should arrive after Email declares and binds its queue")
        } finally {
            declareEmailQueueTopology()
        }
    }
}
