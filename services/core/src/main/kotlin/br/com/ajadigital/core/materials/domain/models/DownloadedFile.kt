package br.com.ajadigital.core.materials.domain.models



data class DownloadedFile(val name: String, val mime: String, val bytes: ByteArray, val inline: Boolean)
