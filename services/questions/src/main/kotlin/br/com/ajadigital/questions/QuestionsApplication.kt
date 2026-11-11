package br.com.ajadigital.questions

import br.com.ajadigital.questions.application.health.CheckServiceHealth
import br.com.ajadigital.questions.ports.out.HealthProbe
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class QuestionsApplication {
    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)
}

fun main(args: Array<String>) {
    runApplication<QuestionsApplication>(*args)
}
