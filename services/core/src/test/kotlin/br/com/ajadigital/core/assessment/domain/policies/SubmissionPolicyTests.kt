package br.com.ajadigital.core.assessment.domain.policies

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SubmissionPolicyTests {
    @Test
    fun `validates feedback grade against maxGrade`() {
        assertTrue(SubmissionPolicy.isValidFeedbackGrade(null, null))
        assertTrue(SubmissionPolicy.isValidFeedbackGrade(BigDecimal("8.5"), BigDecimal("10.0")))
        assertTrue(SubmissionPolicy.isValidFeedbackGrade(BigDecimal("10.0"), BigDecimal("10.0")))
        assertFalse(SubmissionPolicy.isValidFeedbackGrade(BigDecimal("5.0"), null))
        assertFalse(SubmissionPolicy.isValidFeedbackGrade(BigDecimal("10.5"), BigDecimal("10.0")))
    }

    @Test
    fun `requires non-blank comment or non-null grade to publish feedback`() {
        assertTrue(SubmissionPolicy.canPublishFeedback("Bom trabalho", null))
        assertTrue(SubmissionPolicy.canPublishFeedback("   ", BigDecimal("9.0")))
        assertFalse(SubmissionPolicy.canPublishFeedback("", null))
        assertFalse(SubmissionPolicy.canPublishFeedback("   ", null))
    }

    @Test
    fun `validates all staged files are locked and unexpired`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val future = Instant.parse("2026-11-16T12:05:00Z")
        assertTrue(SubmissionPolicy.areStagedFilesValid(emptyList(), emptyMap(), now))
        assertTrue(SubmissionPolicy.areStagedFilesValid(listOf("f1"), mapOf("f1" to future), now))
        assertFalse(SubmissionPolicy.areStagedFilesValid(listOf("f1", "f2"), mapOf("f1" to future), now))
        assertFalse(SubmissionPolicy.areStagedFilesValid(listOf("f1"), mapOf("f1" to now), now))
    }
}
