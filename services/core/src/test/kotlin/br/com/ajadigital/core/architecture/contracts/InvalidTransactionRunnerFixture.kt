package br.com.ajadigital.core.architecture.transactioninvalid.application.contracts

data class TransactionWrapper<T>(val block: () -> T)

interface TransactionRunnerOutputPort {
    fun <T> run(request: TransactionWrapper<T>): T
}
