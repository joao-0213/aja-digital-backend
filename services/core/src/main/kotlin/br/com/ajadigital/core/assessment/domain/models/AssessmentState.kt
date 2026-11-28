package br.com.ajadigital.core.assessment.domain.models

import java.time.Instant

sealed interface AssessmentState {
    val label: String
    val publishedAt: Instant?

    data object Draft : AssessmentState {
        override val label = "draft"
        override val publishedAt: Instant? = null
    }

    data class Published(
        override val publishedAt: Instant,
    ) : AssessmentState {
        override val label = "published"
    }
}
