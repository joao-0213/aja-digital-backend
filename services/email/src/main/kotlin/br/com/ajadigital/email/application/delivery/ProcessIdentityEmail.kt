package br.com.ajadigital.email.application.delivery

import br.com.ajadigital.email.ports.out.DeliveryAttempt
import br.com.ajadigital.email.ports.out.DeliveryFailure
import br.com.ajadigital.email.ports.out.DeliveryOutcome
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import br.com.ajadigital.email.ports.out.EmailSender

data class IdentityEmail(val eventId: String, val eventType: String, val recipient: String, val token: String, val purpose: String)

class ProcessIdentityEmail(private val store: EmailDeliveryStore, private val sender: EmailSender) {
    fun process(email: IdentityEmail): DeliveryOutcome = when (val attempt = store.begin(email.eventId, email.eventType)) {
        DeliveryAttempt.Acknowledge, DeliveryAttempt.Interrupted -> DeliveryOutcome.ACKNOWLEDGED
        DeliveryAttempt.Dead -> DeliveryOutcome.DEAD
        is DeliveryAttempt.Send -> try {
            sender.send(email.eventId, email.recipient, email.token, email.purpose)
            store.setStatus(email.eventId, "sent", null)
            DeliveryOutcome.ACKNOWLEDGED
        } catch (failure: DeliveryFailure) {
            when {
                failure.ambiguous -> {
                    store.setStatus(email.eventId, "unknown", failure.javaClass.simpleName)
                    DeliveryOutcome.ACKNOWLEDGED
                }
                attempt.count >= MAX_ATTEMPTS -> {
                    store.setStatus(email.eventId, "dead", failure.javaClass.simpleName)
                    DeliveryOutcome.DEAD
                }
                else -> {
                    store.setStatus(email.eventId, "retry", failure.javaClass.simpleName)
                    DeliveryOutcome.RETRY
                }
            }
        } catch (failure: Exception) {
            store.setStatus(email.eventId, "unknown", failure.javaClass.simpleName)
            DeliveryOutcome.ACKNOWLEDGED
        }
    }

    companion object { private const val MAX_ATTEMPTS = 5 }
}
