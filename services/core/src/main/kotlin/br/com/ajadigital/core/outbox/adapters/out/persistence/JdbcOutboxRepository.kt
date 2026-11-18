package br.com.ajadigital.core.outbox.adapters.out.persistence

import br.com.ajadigital.core.outbox.domain.models.PendingOutboxEvent
import br.com.ajadigital.core.outbox.domain.models.ClaimPendingOutboxEventsDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventPublishedDto
import br.com.ajadigital.core.outbox.domain.models.MarkOutboxEventFailedDto
import br.com.ajadigital.core.outbox.domain.models.RedriveStoredOutboxEventDto
import br.com.ajadigital.core.outbox.domain.ports.out.OutboxRepositoryOutputPort
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant

class JdbcOutboxRepository(
    private val jdbc: JdbcTemplate,
) : OutboxRepositoryOutputPort {
    override fun claimPending(request: ClaimPendingOutboxEventsDto): List<PendingOutboxEvent> = jdbc.query(
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
        Timestamp.from(request.now),
        request.limit,
    )

    override fun markPublished(request: MarkOutboxEventPublishedDto) {
        // A crash after RabbitMQ confirms but before this update is an intentional at-least-once redelivery window.
        jdbc.update(
            "update core.outbox_events set published_at=?,attempt_count=attempt_count+1,last_error=null where id=?",
            Timestamp.from(request.now),
            request.id,
        )
    }

    override fun markFailedAttempt(request: MarkOutboxEventFailedDto) {
        jdbc.update(
            """update core.outbox_events set attempt_count=?,next_attempt_at=?,last_error=?,failed_at=? where id=?""",
            request.attemptCount,
            Timestamp.from(request.nextAttemptAt),
            request.lastError,
            request.failedAt?.let { Timestamp.from(it) },
            request.id,
        )
    }

    override fun redrive(request: RedriveStoredOutboxEventDto): Boolean = jdbc.update(
        """update core.outbox_events set attempt_count=0,next_attempt_at=?,last_error=null,failed_at=null
           where id=? and published_at is null and failed_at is not null""",
        Timestamp.from(request.now),
        request.id,
    ) == 1
}
