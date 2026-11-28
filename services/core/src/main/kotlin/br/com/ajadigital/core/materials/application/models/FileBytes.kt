package br.com.ajadigital.core.materials.application.models

class FileBytes(
    bytes: ByteArray,
) {
    private val content = bytes.copyOf()
    val size: Int get() = content.size

    fun header(): ByteArray = content.copyOf(minOf(SIGNATURE_HEADER_BYTES, content.size))

    fun copy(): ByteArray = content.copyOf()
}

private const val SIGNATURE_HEADER_BYTES = 12
