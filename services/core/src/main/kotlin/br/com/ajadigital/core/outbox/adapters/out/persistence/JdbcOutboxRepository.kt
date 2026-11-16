package br.com.ajadigital.core.outbox.adapters.out.persistence

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepository
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant

class JdbcOutboxRepository(
    private val jdbc: JdbcTemplate,
) : OutboxRepository {
    override fun claimPending(now: Instant, limit: Int): List<PendingOutboxEvent> = jdbc.query(
        """select id,event_type,event_version,key_id,nonce,encrypted_payload,attempt_count
           from core.outbox_events where published_at is null and failed_at is null and next_attempt_at<=?
           order by created_at for update skip locked limit ?""",
        { rs, _ ->
            PendingOutboxEvent(
                rs.getString("id"),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getString("key_id"),
                rs.getBytes("nonce"),
                rs.getBytes("encrypted_payload"),
                rs.getInt("attempt_count"),
            )
        },
        Timestamp.from(now),
        limit,
    )

    override fun markPublished(id: String, now: Instant) {
        // A crash after RabbitMQ confirms but before this update is an intentional at-least-once redelivery window.
        jdbc.update(
            "update core.outbox_events set published_at=?,attempt_count=attempt_count+1,last_error=null where id=?",
            Timestamp.from(now),
            id,
        )
    }

    override fun markFailedAttempt(
        id: String,
        attemptCount: Int,
        nextAttemptAt: Instant,
        lastError: String,
        failedAt: Instant?,
    ) {
        jdbc.update(
            """update core.outbox_events set attempt_count=?,next_attempt_at=?,last_error=?,failed_at=? where id=?""",
            attemptCount,
            Timestamp.from(nextAttemptAt),
            lastError,
            failedAt?.let { Timestamp.from(it) },
            id,
        )
    }

    override fun redrive(id: String, now: Instant): Boolean = jdbc.update(
        """update core.outbox_events set attempt_count=0,next_attempt_at=?,last_error=null,failed_at=null
           where id=? and published_at is null and failed_at is not null""",
        Timestamp.from(now),
        id,
    ) == 1
}
