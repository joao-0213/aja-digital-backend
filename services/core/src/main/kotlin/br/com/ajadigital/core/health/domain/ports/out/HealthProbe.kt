package br.com.ajadigital.core.health.domain.ports.out

fun interface HealthProbe {
    fun dependenciesAvailable(): Boolean
}
