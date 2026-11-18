package br.com.ajadigital.core.materials.application.models

class FileBytes(bytes: ByteArray) {
    private val content = bytes.copyOf()
    val size: Int get() = content.size

    fun header(): ByteArray = content.copyOf(minOf(12, content.size))
    fun copy(): ByteArray = content.copyOf()
}
