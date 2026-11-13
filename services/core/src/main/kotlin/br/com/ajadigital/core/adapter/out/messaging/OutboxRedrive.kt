package br.com.ajadigital.core.adapter.out.messaging

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant


@Component
class OutboxRedrive(private val jdbc: JdbcTemplate, private val clock: Clock) {
    fun redrive(id: String): Boolean = jdbc.update(
        """update core.outbox_events set attempt_count=0,next_attempt_at=?,last_error=null,failed_at=null
           where id=? and published_at is null and failed_at is not null""",
        Timestamp.from(Instant.now(clock)), id,
    ) == 1
}
