package br.com.ajadigital.core.materials.application.models

enum class UploadPurpose(
    val value: String,
) {
    MATERIAL("material"),
    WORK("work"),
    FORUM("forum"),
    ;

    companion object {
        fun parse(value: String): UploadPurpose? = entries.find { it.value == value }
    }
}
