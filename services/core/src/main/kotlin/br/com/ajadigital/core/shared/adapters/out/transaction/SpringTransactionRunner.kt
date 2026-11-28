package br.com.ajadigital.core.shared.adapters.out.transaction

import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.springframework.transaction.support.TransactionTemplate

class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunnerOutputPort {
    override fun <T> run(block: () -> T): T = template.execute { block() }
}
