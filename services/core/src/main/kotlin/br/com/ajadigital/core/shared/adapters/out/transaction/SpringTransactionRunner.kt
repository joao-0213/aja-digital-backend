package br.com.ajadigital.core.shared.adapters.out.transaction

import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto
import org.springframework.transaction.support.TransactionTemplate

class SpringTransactionRunner(private val template: TransactionTemplate) : TransactionRunnerOutputPort {
    override fun <T> run(request: TransactionRunDto<T>): T = template.execute { request.block() }
}
