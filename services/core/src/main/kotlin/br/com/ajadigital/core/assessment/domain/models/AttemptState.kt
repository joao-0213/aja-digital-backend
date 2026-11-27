package br.com.ajadigital.core.assessment.domain.models

import java.time.Instant

sealed interface AttemptState {
    data object InProgress : AttemptState
    data class Submitted(val submittedAt: Instant) : AttemptState

    companion object {
        fun from(submittedAt: String?): AttemptState = submittedAt
            ?.let { Submitted(Instant.parse(it)) }
            ?: InProgress
    }
}
