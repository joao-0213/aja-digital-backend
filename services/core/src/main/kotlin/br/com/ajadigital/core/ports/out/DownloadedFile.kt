package br.com.ajadigital.core.ports.out



data class DownloadedFile(val name: String, val mime: String, val bytes: ByteArray, val inline: Boolean)
