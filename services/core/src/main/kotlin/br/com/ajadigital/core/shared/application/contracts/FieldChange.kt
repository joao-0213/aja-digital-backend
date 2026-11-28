package br.com.ajadigital.core.shared.application.contracts

sealed interface FieldChange<out T> {
    data object Unchanged : FieldChange<Nothing>

    data class Set<T>(
        val value: T,
    ) : FieldChange<T>
}

fun <T : Any> FieldChange<T>.valueOrNull(): T? = when (this) {
    FieldChange.Unchanged -> null
    is FieldChange.Set -> value
}
