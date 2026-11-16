package br.com.ajadigital.core.outbox.domain.ports.`in`

interface PublishPendingEventsUseCase {
    fun publishAvailable()
}
