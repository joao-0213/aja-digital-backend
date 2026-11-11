package br.com.ajadigital.questions.ports.out

fun interface HealthProbe {
    fun dependenciesAvailable(): Boolean
}
