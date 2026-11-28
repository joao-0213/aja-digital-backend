package br.com.ajadigital.core.shared.application.ports.out

interface TransactionRunnerOutputPort {
    fun <T> run(block: () -> T): T
}
