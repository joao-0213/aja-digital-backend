package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionView
import br.com.ajadigital.core.assessment.domain.models.AssessmentState
import java.time.Instant
import java.time.ZoneId

object AssessmentEditPolicy {
    private val saoPauloZone: ZoneId = ZoneId.of("America/Sao_Paulo")

    data class PostSubmissionDeadlineResolution(val allowed: Boolean, val resolvedDeadline: Instant?)

    fun isValidKindQuestionCount(kind: AssessmentKind, questionCount: Int): Boolean =
        when (kind) {
            AssessmentKind.WORK -> questionCount == 0
            AssessmentKind.QUIZ -> questionCount > 0
        }

    fun canPublish(state: AssessmentState, hasSubmissions: Boolean, kind: AssessmentKind, questionCount: Int): Boolean =
        state == AssessmentState.Draft && !hasSubmissions && isValidKindQuestionCount(kind, questionCount)

    fun questionsChanged(current: List<AssessmentQuestionView>, desired: List<AssessmentQuestion>): Boolean =
        current.size != desired.size || current.indices.any { index ->
            val prior = current[index]
            val next = desired[index]
            prior.id != next.id || prior.origin != next.source || next.snapshot?.let { it != prior.content } == true
        }

    fun edit(
        current: AssessmentDraft,
        requested: AssessmentDraft,
        hasSubmissions: Boolean,
        changedQuestions: Boolean,
        confirmResetDrafts: Boolean,
    ): AssessmentDraft? {
        if (changedQuestions && !confirmResetDrafts) return null
        if (!hasSubmissions) return requested

        val sameStructure = current.moduleId == requested.moduleId && current.kind == requested.kind &&
            current.maxGrade == requested.maxGrade && current.required == requested.required &&
            current.allowLate == requested.allowLate && !changedQuestions
        val resolution = resolvePostSubmissionDeadline(sameStructure, current.deadline, requested.deadline)
        return requested.copy(deadline = resolution.resolvedDeadline).takeIf { resolution.allowed }
    }

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
