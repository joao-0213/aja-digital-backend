package br.com.ajadigital.core.materials.domain.policies

internal object FileRules {
    fun validFilename(name: String): Boolean = name.isNotBlank() && name.length <= MAX_FILENAME_LENGTH && name.none { it.isISOControl() } && '/' !in name && '\\' !in name

    fun validFileSignature(
        name: String,
        mime: String,
        header: ByteArray,
    ): Boolean {
        if (header.isEmpty()) return false
        return when (mime.lowercase()) {
            "application/pdf" -> name.endsWith(".pdf", true) && header.toString(Charsets.US_ASCII).startsWith("%PDF-")
            "image/png" ->
                name.endsWith(".png", true) &&
                    header.hasPrefix(PNG_SIGNATURE)
            "image/jpeg" ->
                (name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)) &&
                    header.hasPrefix(JPEG_SIGNATURE)
            "image/webp" ->
                name.endsWith(".webp", true) && header.size >= WEBP_HEADER_LENGTH &&
                    header.copyOfRange(0, RIFF_TAG_LENGTH).toString(Charsets.US_ASCII) == "RIFF" &&
                    header.copyOfRange(WEBP_TAG_OFFSET, WEBP_HEADER_LENGTH).toString(Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    private fun ByteArray.hasPrefix(signature: List<Int>): Boolean = take(signature.size).map { it.toInt() and UNSIGNED_BYTE_MASK } == signature

    fun normalizedMime(value: String): String = value.substringBefore(';').trim().lowercase()
}

private const val MAX_FILENAME_LENGTH = 255
private const val UNSIGNED_BYTE_MASK = 0xff
private const val RIFF_TAG_LENGTH = 4
private const val WEBP_TAG_OFFSET = 8
private const val WEBP_HEADER_LENGTH = 12
private val PNG_SIGNATURE = listOf(137, 80, 78, 71, 13, 10, 26, 10)
private val JPEG_SIGNATURE = listOf(255, 216, 255)
