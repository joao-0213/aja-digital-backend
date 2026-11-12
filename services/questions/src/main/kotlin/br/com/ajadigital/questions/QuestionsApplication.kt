package br.com.ajadigital.questions

import br.com.ajadigital.questions.application.health.CheckServiceHealth
import br.com.ajadigital.questions.application.question.ReadQuestionCatalog
import br.com.ajadigital.questions.ports.out.HealthProbe
import br.com.ajadigital.questions.ports.out.QuestionCatalog
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class QuestionsApplication {
    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)

    @Bean
    fun readQuestionCatalog(catalog: QuestionCatalog) = ReadQuestionCatalog(catalog)
}

fun main(args: Array<String>) {
    runApplication<QuestionsApplication>(*args)
}
