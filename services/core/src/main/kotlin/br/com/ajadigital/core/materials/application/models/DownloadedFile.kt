package br.com.ajadigital.core.materials.application.models

class DownloadedFile(
    val name: String,
    val mime: String,
    bytes: ByteArray,
    val inline: Boolean,
) {
    private val content = bytes.copyOf()
    val bytes: ByteArray get() = content.copyOf()
}
