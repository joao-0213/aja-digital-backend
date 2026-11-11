package br.com.ajadigital.email

import br.com.ajadigital.email.application.health.CheckServiceHealth
import br.com.ajadigital.email.ports.out.HealthProbe
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class EmailApplication {
    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)
}

fun main(args: Array<String>) {
    runApplication<EmailApplication>(*args)
}
