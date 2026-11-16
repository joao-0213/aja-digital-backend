package br.com.ajadigital.core.shared.adapters.out.transaction

import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.transaction.support.TransactionTemplate

class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunner {
    override fun <T> run(block: () -> T): T = template.execute { block() }
}
