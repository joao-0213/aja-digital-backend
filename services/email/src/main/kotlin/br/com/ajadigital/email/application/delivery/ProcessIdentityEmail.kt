package br.com.ajadigital.email.application.delivery

import br.com.ajadigital.email.ports.out.DeliveryAttempt
import br.com.ajadigital.email.ports.out.DeliveryFailure
import br.com.ajadigital.email.ports.out.DeliveryOutcome
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import br.com.ajadigital.email.ports.out.EmailSender

data class IdentityEmail(val eventId: String, val eventType: String, val recipient: String, val token: String, val purpose: String)
data class AnnouncementEmail(val eventId: String, val eventType: String, val recipient: String, val title: String, val body: String)

class ProcessIdentityEmail(private val store: EmailDeliveryStore, private val sender: EmailSender) {
    fun process(email: IdentityEmail): DeliveryOutcome = process(email.eventId, email.eventType) {
        sender.send(email.eventId, email.recipient, email.token, email.purpose)
    }

    fun processAnnouncement(email: AnnouncementEmail): DeliveryOutcome = process(email.eventId, email.eventType) {
        sender.sendAnnouncement(email.eventId, email.recipient, email.title, email.body)
    }

    private fun process(eventId: String, eventType: String, send: () -> Unit): DeliveryOutcome = when (val attempt = store.begin(eventId, eventType)) {
        DeliveryAttempt.Acknowledge, DeliveryAttempt.Interrupted -> DeliveryOutcome.ACKNOWLEDGED
        DeliveryAttempt.Dead -> DeliveryOutcome.DEAD
        is DeliveryAttempt.Send -> try {
            send()
            store.setStatus(eventId, "sent", null)
            DeliveryOutcome.ACKNOWLEDGED
        } catch (failure: DeliveryFailure) {
            when {
                failure.ambiguous -> {
                    store.setStatus(eventId, "unknown", failure.javaClass.simpleName)
                    DeliveryOutcome.ACKNOWLEDGED
                }
                attempt.count >= MAX_ATTEMPTS -> {
                    store.setStatus(eventId, "dead", failure.javaClass.simpleName)
                    DeliveryOutcome.DEAD
                }
                else -> {
                    store.setStatus(eventId, "retry", failure.javaClass.simpleName)
                    DeliveryOutcome.RETRY
                }
            }
        } catch (failure: Exception) {
            store.setStatus(eventId, "unknown", failure.javaClass.simpleName)
            DeliveryOutcome.ACKNOWLEDGED
        }
    }

    companion object { private const val MAX_ATTEMPTS = 5 }
}
