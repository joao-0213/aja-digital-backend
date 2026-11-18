package br.com.ajadigital.core.materials.domain.policies

internal object FileRules {
    fun validFilename(name: String): Boolean =
        name.isNotBlank() && name.length <= 255 && name.none { it.isISOControl() } && '/' !in name && '\\' !in name

    fun validFileSignature(name: String, mime: String, header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        return when (mime.lowercase()) {
            "application/pdf" -> name.endsWith(".pdf", true) && header.toString(Charsets.US_ASCII).startsWith("%PDF-")
            "image/png" -> name.endsWith(".png", true) && header.take(8).map { it.toInt() and 0xff } == listOf(137, 80, 78, 71, 13, 10, 26, 10)
            "image/jpeg" -> (name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)) && header.take(3).map { it.toInt() and 0xff } == listOf(255, 216, 255)
            "image/webp" -> name.endsWith(".webp", true) && header.size >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    fun normalizedMime(value: String): String = value.substringBefore(';').trim().lowercase()
}
