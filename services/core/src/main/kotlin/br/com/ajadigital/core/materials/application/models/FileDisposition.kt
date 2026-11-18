package br.com.ajadigital.core.materials.application.models

enum class FileDisposition(val value: String) {
    INLINE("inline"), ATTACHMENT("attachment");

    companion object {
        fun parse(value: String): FileDisposition? = entries.find { it.value == value }
    }
}
