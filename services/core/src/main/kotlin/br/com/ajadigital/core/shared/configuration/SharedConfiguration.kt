package br.com.ajadigital.core.shared.configuration

import br.com.ajadigital.core.shared.adapters.out.transaction.SpringTransactionRunner
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

@Configuration
class SharedConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        SpringTransactionRunner(TransactionTemplate(transactionManager))
}
