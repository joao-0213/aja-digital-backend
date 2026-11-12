package br.com.ajadigital.email.application.delivery

import br.com.ajadigital.email.ports.out.EmailDeadLetterQueue
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import br.com.ajadigital.email.ports.out.RedriveClaim

enum class RedriveResult { ACCEPTED, IN_PROGRESS, NOT_ELIGIBLE }

class RedriveDeadEmail(private val store: EmailDeliveryStore, private val deadLetterQueue: EmailDeadLetterQueue) {
    fun redrive(eventId: String): RedriveResult = when (store.claimRedrive(eventId)) {
        RedriveClaim.IN_PROGRESS -> RedriveResult.IN_PROGRESS
        RedriveClaim.NOT_ELIGIBLE -> RedriveResult.NOT_ELIGIBLE
        RedriveClaim.CLAIMED -> try {
            if (deadLetterQueue.republish(eventId)) {
                store.finishRedrive(eventId)
                RedriveResult.ACCEPTED
            } else {
                store.releaseRedrive(eventId)
                RedriveResult.NOT_ELIGIBLE
            }
        } catch (failure: Exception) {
            store.releaseRedrive(eventId)
            throw failure
        }
    }
}
