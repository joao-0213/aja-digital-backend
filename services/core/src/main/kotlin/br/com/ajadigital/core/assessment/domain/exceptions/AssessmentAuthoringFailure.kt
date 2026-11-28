package br.com.ajadigital.core.assessment.domain.exceptions

sealed class AssessmentAuthoringFailure : RuntimeException() {
    class Forbidden : AssessmentAuthoringFailure()

    class NotFound : AssessmentAuthoringFailure()

    class Conflict(
        val currentRevision: Int? = null,
    ) : AssessmentAuthoringFailure()

    class IdempotencyConflict : AssessmentAuthoringFailure()
}
