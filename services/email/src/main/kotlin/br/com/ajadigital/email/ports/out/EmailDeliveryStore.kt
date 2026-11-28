package br.com.ajadigital.email.ports.out

sealed interface DeliveryAttempt {
    data class Send(
        val count: Int,
    ) : DeliveryAttempt

    data object Acknowledge : DeliveryAttempt

    data object Dead : DeliveryAttempt

    data object Interrupted : DeliveryAttempt
}

enum class DeliveryOutcome { ACKNOWLEDGED, RETRY, DEAD }

enum class RedriveClaim { CLAIMED, IN_PROGRESS, NOT_ELIGIBLE }

interface EmailDeliveryStore {
    fun begin(
        eventId: String,
        eventType: String,
    ): DeliveryAttempt

    fun setStatus(
        eventId: String,
        status: String,
        error: String?,
    )

    fun recordPoison(
        eventId: String,
        eventType: String,
    )

    fun claimRedrive(eventId: String): RedriveClaim

    fun finishRedrive(eventId: String)

    fun releaseRedrive(eventId: String)
}

interface EmailDeadLetterQueue {
    fun republish(eventId: String): Boolean
}
