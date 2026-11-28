package br.com.ajadigital.core.outbox.application.contracts

class PrepareOutboxEventCommand(
    val id: String,
    val eventType: String,
    val eventVersion: Int,
    payload: ByteArray,
) {
    private val payloadBytes = payload.copyOf()
    val payload: ByteArray get() = payloadBytes.copyOf()
}
