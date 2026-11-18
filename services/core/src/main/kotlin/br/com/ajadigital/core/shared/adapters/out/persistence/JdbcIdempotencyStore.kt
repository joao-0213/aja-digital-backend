package br.com.ajadigital.core.shared.adapters.out.persistence

import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Clock

@Repository
class JdbcIdempotencyStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock) : IdempotencyStoreOutputPort {
    override fun find(request: FindIdempotencyDto): Map<String, Any?>? {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0)) IS NULL", Boolean::class.java, "${request.actorId}|${request.operation}|${request.target}|${request.key}")
        val record = jdbc.query("SELECT request_hash,response_body FROM core.command_idempotency WHERE actor_id=? AND operation=? AND target=? AND idempotency_key=? AND expires_at>?", { rs, _ -> rs.getString(1) to rs.getString(2) }, request.actorId, request.operation, request.target, request.key, Timestamp.from(clock.instant())).firstOrNull() ?: return null
        if (record.first != request.requestHash) throw IdempotencyKeyReuse()
        @Suppress("UNCHECKED_CAST")
        return mapper.readValue(record.second, Map::class.java) as Map<String, Any?>
    }

    override fun save(request: SaveIdempotencyDto) {
        val now = Timestamp.from(clock.instant())
        jdbc.update("""INSERT INTO core.command_idempotency(actor_id,operation,target,idempotency_key,request_hash,response_body,created_at,expires_at)
            VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(actor_id,operation,target,idempotency_key) DO UPDATE SET request_hash=EXCLUDED.request_hash,response_body=EXCLUDED.response_body,created_at=EXCLUDED.created_at,expires_at=EXCLUDED.expires_at
            WHERE core.command_idempotency.expires_at<=EXCLUDED.created_at""",
            request.actorId, request.operation, request.target, request.key, request.requestHash, mapper.writeValueAsString(request.response), now, Timestamp.from(clock.instant().plusSeconds(86400)))
    }
}
