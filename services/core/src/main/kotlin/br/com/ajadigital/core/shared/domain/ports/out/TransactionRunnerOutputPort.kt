package br.com.ajadigital.core.shared.domain.ports.out

import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

interface TransactionRunnerOutputPort {
    fun <T> run(block: () -> T): T = run(TransactionRunDto(block))

    fun <T> run(request: TransactionRunDto<T>): T
}
