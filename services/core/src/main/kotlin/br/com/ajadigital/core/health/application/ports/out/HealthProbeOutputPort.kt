package br.com.ajadigital.core.health.application.ports.out

fun interface HealthProbeOutputPort {
    fun dependenciesAvailable(): Boolean
}
