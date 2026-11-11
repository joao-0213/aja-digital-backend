package br.com.ajadigital.email.ports.out

fun interface HealthProbe {
    fun dependenciesAvailable(): Boolean
}
