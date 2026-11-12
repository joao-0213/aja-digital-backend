package br.com.ajadigital.email.adapter.out.persistence

import br.com.ajadigital.email.ports.out.DeliveryAttempt
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import br.com.ajadigital.email.ports.out.RedriveClaim
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant

@Repository
class JdbcEmailDeliveryStore(
    private val jdbc: JdbcTemplate,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : EmailDeliveryStore {
    override fun begin(eventId: String, eventType: String): DeliveryAttempt = transactions.execute {
        val now = Timestamp.from(Instant.now(clock))
        jdbc.update(
            "insert into email.delivery_events(event_id,event_type,status,created_at,updated_at) values(?,?,'pending',?,?) on conflict(event_id) do nothing",
            eventId, eventType, now, now,
        )
        val row = jdbc.queryForMap("select status,attempt_count from email.delivery_events where event_id=? for update", eventId)
        when (row["status"] as String) {
            "sent", "unknown" -> DeliveryAttempt.Acknowledge
            "dead" -> DeliveryAttempt.Dead
            "sending" -> {
                jdbc.update("update email.delivery_events set status='unknown',updated_at=?,last_error='InterruptedSend' where event_id=?", now, eventId)
                DeliveryAttempt.Interrupted
            }
            else -> {
                val count = (row["attempt_count"] as Number).toInt() + 1
                jdbc.update("update email.delivery_events set status='sending',attempt_count=?,updated_at=?,last_error=null where event_id=?", count, now, eventId)
                DeliveryAttempt.Send(count)
            }
        }
    } ?: error("Could not begin email delivery")

    override fun setStatus(eventId: String, status: String, error: String?) {
        jdbc.update("update email.delivery_events set status=?,updated_at=?,last_error=? where event_id=?", status, Timestamp.from(Instant.now(clock)), error, eventId)
    }

    override fun recordPoison(eventId: String, eventType: String) {
        jdbc.update(
            "insert into email.delivery_events(event_id,event_type,status,last_error) values(?,?,'dead','InvalidEvent') on conflict(event_id) do nothing",
            eventId, eventType.take(128),
        )
    }

    override fun claimRedrive(eventId: String): RedriveClaim = transactions.execute {
        val claimed = jdbc.update(
            """update email.delivery_events set status='redriving',attempt_count=0,updated_at=?,redrive_started_at=?,last_error=null
               where event_id=? and (status='dead' or (status='redriving' and redrive_started_at < ?))""",
            Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)), eventId,
            Timestamp.from(Instant.now(clock).minusSeconds(30)),
        )
        if (claimed == 1) RedriveClaim.CLAIMED
        else {
            val status = jdbc.query("select status from email.delivery_events where event_id=?", { rs, _ -> rs.getString(1) }, eventId).firstOrNull()
            if (status == "redriving") RedriveClaim.IN_PROGRESS else RedriveClaim.NOT_ELIGIBLE
        }
    } ?: error("Could not claim email DLQ redrive")

    override fun finishRedrive(eventId: String) {
        jdbc.update(
            "update email.delivery_events set status='retry',redrive_started_at=null,updated_at=? where event_id=? and status='redriving'",
            Timestamp.from(Instant.now(clock)), eventId,
        )
    }

    override fun releaseRedrive(eventId: String) {
        jdbc.update(
            "update email.delivery_events set status='dead',redrive_started_at=null,updated_at=? where event_id=? and status='redriving'",
            Timestamp.from(Instant.now(clock)), eventId,
        )
    }
}
