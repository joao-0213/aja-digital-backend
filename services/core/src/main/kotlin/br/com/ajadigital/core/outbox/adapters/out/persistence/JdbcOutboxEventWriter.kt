package br.com.ajadigital.core.outbox.adapters.out.persistence

import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.outbox.application.ports.out.InsertOutboxEventOutputPort
import org.springframework.jdbc.core.JdbcTemplate

class JdbcOutboxEventWriter(
    private val jdbc: JdbcTemplate,
) : InsertOutboxEventOutputPort {
    override fun insert(request: AppendPreparedOutboxEventCommand) {
        val event = request.event
        jdbc.update(
            "INSERT INTO core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload) VALUES(?,?,?,?,?,?,?)",
            event.id,
            event.eventType,
            event.eventVersion,
            java.sql.Timestamp.from(request.createdAt),
            event.keyId,
            event.nonce,
            event.ciphertext,
        )
    }
}
