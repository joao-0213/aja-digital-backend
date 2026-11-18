package br.com.ajadigital.core.materials.application.contracts

enum class MaterialKind(val value: String) {
    LESSON("lesson"), PDF("pdf"), VIDEO("video");

    companion object {
        fun parse(value: String): MaterialKind? = entries.find { it.value == value }
    }
}
