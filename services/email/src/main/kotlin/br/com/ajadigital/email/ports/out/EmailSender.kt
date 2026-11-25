package br.com.ajadigital.email.ports.out

interface EmailSender {
    fun send(eventId: String, recipient: String, token: String, purpose: String)
    fun sendAnnouncement(eventId: String, recipient: String, title: String, body: String, announcementId: String) = send(eventId, recipient, "$title\n\n$body", "announcement")
}

class DeliveryFailure(val ambiguous: Boolean, cause: Throwable? = null) : RuntimeException(cause)
