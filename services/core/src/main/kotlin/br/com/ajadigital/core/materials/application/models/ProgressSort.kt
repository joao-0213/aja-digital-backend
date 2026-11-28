package br.com.ajadigital.core.materials.application.models

enum class ProgressSort(
    val value: String,
) {
    ASCENDING("id"),
    DESCENDING("-id"),
    ;

    companion object {
        fun parse(value: String): ProgressSort? = entries.find { it.value == value }
    }
}
