package br.com.ajadigital.core.assessment.domain.policies

import java.math.BigDecimal
import java.time.Instant

object SubmissionPolicy {
    fun isValidFeedbackGrade(grade: BigDecimal?, maxGrade: BigDecimal?): Boolean =
        grade == null || (maxGrade != null && grade <= maxGrade)

    fun canPublishFeedback(comment: String, grade: Any?): Boolean =
        !(comment.isBlank() && grade == null)

    fun areStagedFilesValid(
        fileIds: List<String>,
        lockedFileExpiries: Map<String, Instant>,
        now: Instant,
    ): Boolean =
        lockedFileExpiries.size == fileIds.size &&
            fileIds.all { fileId -> lockedFileExpiries[fileId]?.isAfter(now) == true }
}
