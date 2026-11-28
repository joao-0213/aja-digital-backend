package br.com.ajadigital.email

import br.com.ajadigital.email.application.delivery.ProcessIdentityEmail
import br.com.ajadigital.email.application.delivery.RedriveDeadEmail
import br.com.ajadigital.email.application.health.CheckServiceHealth
import br.com.ajadigital.email.ports.out.EmailDeadLetterQueue
import br.com.ajadigital.email.ports.out.EmailDeliveryStore
import br.com.ajadigital.email.ports.out.EmailSender
import br.com.ajadigital.email.ports.out.HealthProbe
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication
class EmailApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun processIdentityEmail(
        store: EmailDeliveryStore,
        sender: EmailSender,
    ) = ProcessIdentityEmail(store, sender)

    @Bean
    fun redriveDeadEmail(
        store: EmailDeliveryStore,
        queue: EmailDeadLetterQueue,
    ) = RedriveDeadEmail(store, queue)

    @Bean
    fun checkServiceHealth(probe: HealthProbe) = CheckServiceHealth(probe)
}

fun main(args: Array<String>) {
    runApplication<EmailApplication>(*args)
}
