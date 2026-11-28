package br.com.ajadigital.core.materials.application.models

enum class MaterialSort(
    val value: String,
) {
    ASCENDING("order"),
    DESCENDING("-order"),
    ;

    companion object {
        fun parse(value: String): MaterialSort? = entries.find { it.value == value }
    }
}
