package br.com.ajadigital.core.outbox.domain.ports.`in`

interface RedriveOutboxEventUseCase {
    fun redrive(id: String): Boolean
}
