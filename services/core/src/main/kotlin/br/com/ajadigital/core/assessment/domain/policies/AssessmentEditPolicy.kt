package br.com.ajadigital.core.assessment.domain.policies

import java.time.Instant
import java.time.ZoneId

object AssessmentEditPolicy {
    private val saoPauloZone: ZoneId = ZoneId.of("America/Sao_Paulo")

    data class PostSubmissionDeadlineResolution(val allowed: Boolean, val resolvedDeadline: Instant?)

    fun isValidKindQuestionCount(kind: String, questionCount: Int): Boolean =
        !((kind == "work" && questionCount != 0) || (kind == "quiz" && questionCount == 0))

    fun canPublish(status: String, hasSubmissions: Boolean, kind: String, questionCount: Int): Boolean =
        status == "draft" && !hasSubmissions && isValidKindQuestionCount(kind, questionCount)

    fun resolvePostSubmissionDeadline(
        sameStructure: Boolean,
        oldDeadline: Instant?,
        requestedDeadline: Instant?,
    ): PostSubmissionDeadlineResolution {
        if (!sameStructure) return PostSubmissionDeadlineResolution(allowed = false, resolvedDeadline = null)
        val oldZoned = oldDeadline?.atZone(saoPauloZone)
        val requestedZoned = requestedDeadline?.atZone(saoPauloZone)
        if ((oldZoned == null) != (requestedZoned == null)) {
            return PostSubmissionDeadlineResolution(allowed = false, resolvedDeadline = null)
        }
        if (oldZoned != null && requestedZoned != null && oldZoned.toLocalTime() != requestedZoned.toLocalTime()) {
            return PostSubmissionDeadlineResolution(allowed = false, resolvedDeadline = null)
        }
        val resolved = if (oldZoned != null && requestedZoned != null) {
            requestedZoned.toLocalDate().atTime(oldZoned.toLocalTime()).atZone(saoPauloZone).toInstant()
        } else {
            requestedDeadline
        }
        return PostSubmissionDeadlineResolution(allowed = true, resolvedDeadline = resolved)
    }
}
