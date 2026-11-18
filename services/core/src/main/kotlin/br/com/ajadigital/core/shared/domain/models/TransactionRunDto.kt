package br.com.ajadigital.core.shared.domain.models

data class TransactionRunDto<T>(val block: () -> T)
