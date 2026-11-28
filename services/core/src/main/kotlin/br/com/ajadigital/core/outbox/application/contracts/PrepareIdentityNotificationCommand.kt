package br.com.ajadigital.core.outbox.application.contracts

class PrepareIdentityNotificationCommand(
    val eventId: String,
    val eventType: String,
    val eventVersion: Int,
    val email: String,
    val token: String,
    val userId: String,
    val purpose: String,
) {
    override fun toString(): String =
        "PrepareIdentityNotificationCommand(eventId=$eventId, eventType=$eventType, eventVersion=$eventVersion, email=$email, token=[redacted], userId=$userId, purpose=$purpose)"
}
