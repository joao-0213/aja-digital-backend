package br.com.ajadigital.core.shared.domain.ports.out

interface TransactionRunner {
    fun <T> run(block: () -> T): T
}
