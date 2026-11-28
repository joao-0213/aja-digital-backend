package br.com.ajadigital.core.materials.domain.models

import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import java.net.URI
import java.net.URISyntaxException

class NewMaterial(
    val moduleId: String,
    val title: String,
    val duration: Double,
    val content: String,
    val source: Source,
) {
    init {
        require(
            MaterialRules.validId(moduleId) && title.isNotBlank() && title.length <= MAX_TITLE_LENGTH && duration in 1.0..MAX_DURATION_MINUTES &&
                content.length <= MAX_CONTENT_LENGTH,
        )
        require(source != Source.Lesson || content.isNotBlank())
    }

    sealed interface Source {
        data object Lesson : Source

        data class Pdf(
            val fileId: String,
        ) : Source {
            init {
                require(MaterialRules.validId(fileId))
            }
        }

        data class Video(
            val url: String,
        ) : Source {
            init {
                require(isValidUrl(url))
            }

            companion object {
                fun isValidUrl(value: String): Boolean = try {
                    URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null }
                } catch (_: URISyntaxException) {
                    false
                }
            }
        }
    }
}

private const val MAX_TITLE_LENGTH = 160
private const val MAX_DURATION_MINUTES = 600.0
private const val MAX_CONTENT_LENGTH = 1_000_000
