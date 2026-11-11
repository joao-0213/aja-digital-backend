package br.com.ajadigital.core.ports.out

fun interface HealthProbe {
    fun dependenciesAvailable(): Boolean
}
