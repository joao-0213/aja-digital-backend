package br.com.ajadigital.email.ports.out

interface EmailSender {
    fun send(eventId: String, recipient: String, token: String, purpose: String)
}

class DeliveryFailure(val ambiguous: Boolean, cause: Throwable? = null) : RuntimeException(cause)
