package br.com.ajadigital.core

import br.com.ajadigital.core.application.health.CheckServiceHealth
import br.com.ajadigital.core.ports.out.HealthProbe
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class CoreApplication {
    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)
}

fun main(args: Array<String>) {
    runApplication<CoreApplication>(*args)
}
